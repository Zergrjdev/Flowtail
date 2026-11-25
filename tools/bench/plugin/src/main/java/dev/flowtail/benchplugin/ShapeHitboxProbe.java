package dev.flowtail.benchplugin;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Isolated experiment for invisible Shulker collision proxies; not used by FlowTail's content engine. */
final class ShapeHitboxProbe implements Listener, CommandExecutor {
    private final BenchMetricsPlugin plugin;
    private final List<Shulker> entities = new ArrayList<>();
    private final List<Phase> phases = new ArrayList<>();
    private final int[] counts = {0, 1, 16, 64, 128};
    private BukkitTask task;
    private World world;
    private Location origin;
    private int phaseIndex;
    private int phaseTick;
    private boolean sampling;
    private boolean running;
    private Phase current;

    private record Phase(int count, List<Double> samples) {}

    ShapeHitboxProbe(BenchMetricsPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (running) {
            sender.sendMessage("Hitbox probe is already running.");
            return true;
        }
        world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int y = Math.min(world.getMaxHeight() - 8, Math.max(spawn.getBlockY() + 32, world.getMinHeight() + 32));
        origin = new Location(world, (spawn.getBlockX() & ~15) + 40.0, y, (spawn.getBlockZ() & ~15) + 40.0);
        phases.clear(); phaseIndex = -1;
        running = true;
        sender.sendMessage("Starting Shulker hitbox probe: 0, 1, 16, 64 and 128 invisible no-AI entities; about 25 seconds per count.");
        plugin.getLogger().info("Shulker hitbox probe started in " + world.getName() + " at " + origin);
        captureGeometry(() -> startNextPhase(sender));
        return true;
    }

    private void captureGeometry(Runnable then) {
        Shulker normal = spawn(origin.clone().add(-4, 0, 0), 1.0);
        Shulker small = spawn(origin.clone().add(-2, 0, 0), 0.5);
        normal.setPeek(0f); small.setPeek(0f);
        StringBuilder json = new StringBuilder("{\"scale_1_peek_0\":").append(box(normal));
        json.append(",\"scale_0_5_peek_0\":").append(box(small));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            normal.setPeek(1f); small.setPeek(1f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                json.append(",\"scale_1_peek_1\":").append(box(normal));
                json.append(",\"scale_0_5_peek_1\":").append(box(small));
                normal.setPeek(0f); small.setPeek(0f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    json.append(",\"scale_1_peek_0_return\":").append(box(normal));
                    json.append(",\"scale_0_5_peek_0_return\":").append(box(small)).append('}');
                    geometryJson = json.toString();
                    removeEntities();
                    then.run();
                }, 40L);
            }, 40L);
        }, 20L);
    }

    private String geometryJson;

    private void startNextPhase(CommandSender sender) {
        removeEntities();
        phaseIndex++;
        if (phaseIndex >= counts.length) { finish(sender); return; }
        int count = counts[phaseIndex];
        current = new Phase(count, new ArrayList<>());
        phaseTick = 0; sampling = false;
        for (int i = 0; i < count; i++) {
            int col = i % 10, row = i / 10;
            spawn(origin.clone().add(col * 1.5, 0, row * 1.5), 0.5);
        }
        plugin.getLogger().info("Hitbox probe phase entities=" + count + " warming for 100 ticks then sampling 200 ticks");
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            phaseTick++;
            sampling = phaseTick > 100 && phaseTick <= 300;
            if (phaseTick >= 300) {
                phases.add(new Phase(current.count(), List.copyOf(current.samples())));
                task.cancel(); task = null;
                removeEntities();
                startNextPhase(sender);
            }
        }, 1L, 1L);
    }

    @EventHandler public void onTickEnd(ServerTickEndEvent event) {
        if (task != null && sampling && current != null) current.samples().add(event.getTickDuration());
    }

    private Shulker spawn(Location at, double scale) {
        Shulker shulker = world.spawn(at, Shulker.class, entity -> {
            entity.setAI(false);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setInvisible(true);
            entity.setPersistent(false);
            entity.setCollidable(true);
            entity.setAttachedFace(BlockFace.DOWN);
            entity.setPeek(0f);
            var size = entity.getAttribute(Attribute.SCALE);
            if (size != null) size.setBaseValue(scale);
        });
        entities.add(shulker);
        return shulker;
    }

    private void finish(CommandSender sender) {
        StringBuilder report = new StringBuilder("{\n  \"created_at\":\"").append(Instant.now())
                .append("\",\n  \"world\":\"").append(escape(world.getName())).append("\",\n  \"entity_type\":\"invisible_no_ai_shulker\",")
                .append("\n  \"measurement\":\"ServerTickEndEvent duration; 100 warmup ticks + 200 samples per scenario\",")
                .append("\n  \"geometry\":").append(geometryJson).append(",\n  \"phases\":[");
        double baseline = mean(phases.getFirst().samples());
        for (int i = 0; i < phases.size(); i++) {
            Phase phase = phases.get(i);
            if (i > 0) report.append(',');
            double avg = mean(phase.samples());
            report.append("\n    {\"entities\":").append(phase.count()).append(",\"samples\":").append(phase.samples().size())
                    .append(String.format(Locale.ROOT, ",\"mean_mspt\":%.4f,\"p50_mspt\":%.4f,\"p95_mspt\":%.4f,\"p99_mspt\":%.4f,\"max_mspt\":%.4f",
                            avg, percentile(phase.samples(), .50), percentile(phase.samples(), .95), percentile(phase.samples(), .99), percentile(phase.samples(), 1.0)));
            if (phase.count() > 0) report.append(String.format(Locale.ROOT, ",\"delta_mspt_per_entity\":%.6f", (avg - baseline) / phase.count()));
            report.append('}');
        }
        report.append("\n  ]\n}\n");
        Path file = plugin.getDataFolder().toPath().resolve("shulker-hitbox-probe.json");
        try { Files.createDirectories(file.getParent()); Files.writeString(file, report.toString()); }
        catch (Exception error) { plugin.getLogger().severe("Could not write hitbox probe result: " + error); }
        plugin.getLogger().info("Hitbox probe finished; results: " + file.toAbsolutePath());
        sender.sendMessage("Hitbox probe finished; see plugins/FlowTailBench/shulker-hitbox-probe.json.");
        current = null; sampling = false; running = false;
    }

    void cleanup() { if (task != null) { task.cancel(); task = null; } removeEntities(); running = false; }
    private void removeEntities() { for (Shulker entity : entities) if (entity.isValid()) entity.remove(); entities.clear(); }
    private static double mean(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        return sorted.get(Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
    }
    private static String box(Shulker entity) {
        BoundingBox b = entity.getBoundingBox();
        return String.format(Locale.ROOT, "{\"width\":%.4f,\"height\":%.4f,\"depth\":%.4f,\"min_y_offset\":%.4f,\"peek\":%.3f}",
                b.getWidthX(), b.getHeight(), b.getWidthZ(), b.getMinY() - entity.getLocation().getY(), entity.getPeek());
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
