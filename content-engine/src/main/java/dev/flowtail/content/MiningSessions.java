package dev.flowtail.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Survival mining of FlowTail blocks (P3-04, ADR-027). A barrier cannot be dug by the client, so the server times the
 * break itself from the block's {@code mining} section and the held tool ({@link MiningRules}), keeps the break going
 * while the player keeps swinging at the block, and then finishes it through the normal break path.
 *
 * <p>Known limits (documented, not hidden): the held item is read when mining starts;
 * the client cannot draw a crack overlay on a barrier, so the effect is a tap sound plus item-texture particles every 4 ticks.
 */
final class MiningSessions implements Listener {
    private record Session(Block block, MiningProgress progress) {}

    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final BlockManager blocks;
    private final ItemPrototypes prototypes;
    private final BiConsumer<Player, Block> finish;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> swung = new HashSet<>();
    private BukkitTask task;

    MiningSessions(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes prototypes, BlockManager blocks,
                   BiConsumer<Player, Block> finish) {
        this.plugin = plugin; this.registry = registry; this.prototypes = prototypes; this.blocks = blocks; this.finish = finish;
    }

    /** The block's {@code mining} section; a block without one is stone-like (hardness 1.5, Entry 0031); an unknown id breaks instantly. */
    MiningRules.Spec specOf(String id) {
        JsonObject definition = registry.blocks().get(id);
        if (definition == null) return MiningRules.Spec.INSTANT;
        if (!definition.has("mining") || !definition.get("mining").isJsonObject()) return MiningRules.Spec.DEFAULT;
        JsonObject mining = definition.getAsJsonObject("mining");
        return MiningRules.Spec.of(number(mining, "hardness"), string(mining, "tool"), string(mining, "tier"), bool(mining, "requires_tool"));
    }

    static MiningRules.Held heldOf(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return held == null || held.getType().isAir() ? MiningRules.Held.HAND : MiningRules.Held.of(held.getType().name());
    }

    /** Whether breaking this block with what the player holds gives the block's drop. */
    boolean dropsFor(Player player, String id) { return MiningRules.canHarvest(specOf(id), heldOf(player)); }

    /** Wears the held tool for one finished break (survival only; unbreaking and the break sound are Paper's job). */
    void damageTool(Player player, String id) {
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        int wear = MiningRules.toolDamage(specOf(id), heldOf(player));
        if (wear > 0) player.damageItemStack(EquipmentSlot.HAND, wear);
    }

    /** Called when a survival player starts hitting a FlowTail block (and again for each hit on its click box). */
    void begin(Player player, Block block) {
        Optional<String> id = blocks.idAt(block);
        if (id.isEmpty()) return;
        UUID uuid = player.getUniqueId();
        Session existing = sessions.get(uuid);
        if (existing != null && same(existing.block(), block)) { swung.add(uuid); return; }
        int efficiency = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.EFFICIENCY);
        int ticks = MiningRules.breakTicks(specOf(id.get()), heldOf(player), efficiency);
        if (ticks < 0) return; // unbreakable in survival
        if (ticks == 0) { Bukkit.getScheduler().runTask(plugin, () -> finish.accept(player, block)); return; }
        sessions.put(uuid, new Session(block, new MiningProgress(ticks)));
        swung.add(uuid);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 1L, 1L);
    }

    /** True when the player looks at the block, or at its interaction entity (plants have no carrier the client can target). */
    private boolean aimedAt(Player player, Block block) {
        Block target = player.getTargetBlockExact(6);
        if (target != null && same(target, block)) return true;
        org.bukkit.entity.Entity entity = player.getTargetEntity(6);
        return entity != null && blocks.projectedBlock(entity).map(found -> same(found, block)).orElse(false);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) swung.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { end(event.getPlayer().getUniqueId(), null); }

    private void tick() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Session session = sessions.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.getGameMode() != GameMode.SURVIVAL
                    || blocks.idAt(session.block()).isEmpty()) { end(uuid, player); continue; }
            MiningProgress.State state = session.progress().tick(aimedAt(player, session.block()), swung.remove(uuid));
            switch (state) {
                case MINING -> {
                    player.sendBlockDamage(session.block().getLocation(), session.progress().fraction()); // no-op visually on a barrier
                    if (session.progress().effectTick()) effects(player, session.block());
                }
                case CANCELLED -> end(uuid, player);
                case DONE -> { end(uuid, player); finish.accept(player, session.block()); }
            }
        }
    }

    /**
     * The mining effect. A barrier has no model, so the vanilla crack overlay cannot be drawn on it; instead every few
     * ticks the block gets its "tap" sound and a few item-texture particles on the face the player is looking from.
     */
    private void effects(Player player, Block block) {
        Optional<String> id = blocks.idAt(block);
        if (id.isEmpty()) return;
        JsonObject definition = registry.blocks().get(id.get());
        String preset = definition != null && definition.has("sounds") && definition.get("sounds").isJsonObject()
                && definition.getAsJsonObject("sounds").has("preset")
                ? definition.getAsJsonObject("sounds").get("preset").getAsString() : null;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        SoundPresets.hitSound(preset).ifPresent(key -> block.getWorld().playSound(center, key, SoundCategory.BLOCKS, 0.6f, 0.9f));
        if (!prototypes.has(id.get())) return;
        Vector toward = player.getEyeLocation().toVector().subtract(center.toVector());
        Location face = toward.lengthSquared() < 1e-6 ? center : center.clone().add(toward.normalize().multiply(0.52));
        block.getWorld().spawnParticle(Particle.ITEM, face, 5, 0.2, 0.2, 0.2, 0.03, prototypes.create(id.get()));
    }

    private void end(UUID uuid, Player player) {
        Session removed = sessions.remove(uuid);
        swung.remove(uuid);
        if (removed != null && player != null && player.isOnline()) player.sendBlockDamage(removed.block().getLocation(), 0f);
        if (sessions.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    private static boolean same(Block a, Block b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ() && a.getWorld().equals(b.getWorld());
    }

    private static Double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static Boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
    }
}
