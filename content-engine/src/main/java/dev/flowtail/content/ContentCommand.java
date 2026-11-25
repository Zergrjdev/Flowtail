package dev.flowtail.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * {@code /content list [blocks|items|paintings|retired]}, {@code /content inspect} (the block you look at),
 * {@code /content missing} (blocks in loaded chunks whose plugin is not installed) and {@code /content selftest}
 * (the server-side Phase 2 checks). Needs permission {@code flowtail.content} (default op). Works from the console
 * too, except {@code inspect}, which needs a player to look at a block.
 */
final class ContentCommand implements Listener {
    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final ItemPrototypes prototypes;
    private final BlockManager blocks;
    private final ContentSelfTest selfTest;

    ContentCommand(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes prototypes, BlockManager blocks) {
        this.plugin = plugin; this.registry = registry; this.prototypes = prototypes; this.blocks = blocks;
        this.selfTest = new ContentSelfTest(plugin, registry, prototypes, blocks);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (!args[0].equalsIgnoreCase("content")) return;
        event.setCancelled(true);
        execute(event.getPlayer(), args);
    }

    /** Console and RCON: the event carries the command without a leading slash. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String[] args = event.getCommand().trim().split("\\s+");
        if (!args[0].equalsIgnoreCase("content")) return;
        event.setCancelled(true);
        execute(event.getSender(), args);
    }

    private void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flowtail.content")) {
            sender.sendMessage(Component.text("You do not have permission to use /content."));
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list" -> list(sender, args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all");
            case "inspect" -> inspect(sender);
            case "missing" -> missing(sender);
            case "selftest" -> selftest(sender);
            default -> sender.sendMessage(Component.text(
                    "Usage: /content list [blocks|items|paintings|retired] | inspect | missing | selftest"));
        }
    }

    private void list(CommandSender sender, String what) {
        boolean all = what.equals("all");
        if (!all && !Set.of("blocks", "items", "paintings", "retired").contains(what)) {
            sender.sendMessage(Component.text("Usage: /content list [blocks|items|paintings|retired]"));
            return;
        }
        if (all || what.equals("blocks")) send(sender, ContentReport.list("blocks", registry.blocks().keySet()));
        if (all || what.equals("items")) send(sender, ContentReport.list("items (including block items)", prototypes.ids()));
        if (all || what.equals("paintings")) send(sender, ContentReport.list("paintings", registry.paintings().keySet()));
        if (all || what.equals("retired")) send(sender, ContentReport.list("retired block ids (plugin removed, ids reserved)", registry.retiredBlocks()));
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("/content inspect needs a player: it inspects the block you look at."));
            return;
        }
        Block block = player.getTargetBlockExact(6);
        Optional<String> id = block == null ? Optional.empty() : blocks.idAt(block);
        if (block == null || id.isEmpty()) {
            player.sendMessage(Component.text("Look at a FlowTail block within 6 blocks."));
            return;
        }
        String position = block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
        send(player, ContentReport.inspect(id.get(), registry.blocks().containsKey(id.get()), position,
                block.getBlockData().getAsString(), blocks.describeProjection(block, id.get())));
    }

    private void missing(CommandSender sender) {
        List<String> recorded = new ArrayList<>();
        int chunks = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) { recorded.addAll(blocks.idsIn(chunk)); chunks++; }
        }
        List<String> lines = ContentReport.missing(recorded, new HashSet<>(registry.blocks().keySet()));
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("No missing content in " + chunks + " loaded chunks."));
            return;
        }
        sender.sendMessage(Component.text("Blocks whose plugin is not installed (kept, never deleted), loaded chunks only:"));
        send(sender, lines);
    }

    private void selftest(CommandSender sender) {
        sender.sendMessage(Component.text("FlowTail self-test: placing a few temporary blocks near "
                + (sender instanceof Player ? "you" : "the world spawn") + ", they are removed again in the same tick..."));
        SelfTestReport report = selfTest.run(sender);
        for (SelfTestReport.Result result : report.results()) {
            NamedTextColor color = switch (result.status()) {
                case PASS -> NamedTextColor.GREEN;
                case FAIL -> NamedTextColor.RED;
                case SKIP -> NamedTextColor.YELLOW;
            };
            sender.sendMessage(Component.text(result.line(), color));
            String line = "selftest " + result.line();
            if (result.status() == SelfTestReport.Status.FAIL) plugin.getLogger().warning(line);
            else plugin.getLogger().info(line);
        }
        sender.sendMessage(Component.text(report.summary(), report.allPassed() ? NamedTextColor.GREEN : NamedTextColor.RED));
        plugin.getLogger().info(report.summary());
        sender.sendMessage(Component.text("This proves server behaviour only. What a client draws still needs the visual checklist in docs/13 section 6."));
    }

    private void send(CommandSender sender, List<String> lines) {
        for (String line : lines) sender.sendMessage(Component.text(line));
    }
}
