package dev.flowtail.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.inventory.ItemStack;

/** Adds custom content ids to /give without replacing vanilla's command for other item ids. */
final class ItemCommandListener implements Listener {
    private final FlowTailContentPlugin plugin;
    private final ItemPrototypes prototypes;
    private final ContentRegistry registry;

    ItemCommandListener(FlowTailContentPlugin plugin, ItemPrototypes prototypes, ContentRegistry registry) {
        this.plugin = plugin; this.prototypes = prototypes; this.registry = registry;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack stack : event.getPlayer().getInventory().getStorageContents()) {
            prototypes.idOf(stack).ifPresent(id -> plugin.getLogger().info("Restored custom item plugin_id="
                    + id.substring(0, id.indexOf(':')) + " content_id=" + id + " count=" + stack.getAmount()
                    + " player=" + event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        prototypes.idOf(event.getItemDrop().getItemStack()).ifPresent(id -> plugin.getLogger().info(
                "Dropped custom item plugin_id=" + namespace(id) + " content_id=" + id
                        + " player=" + event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        prototypes.idOf(event.getItem().getItemStack()).ifPresent(id -> plugin.getLogger().info(
                "Picked up custom item plugin_id=" + namespace(id) + " content_id=" + id
                        + " player=" + player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestInventory(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        ItemStack candidate = cursor != null && !cursor.getType().isAir() ? cursor : current;
        prototypes.idOf(candidate).ifPresent(id -> plugin.getLogger().info(
                "Chest interaction with custom item plugin_id=" + namespace(id) + " content_id=" + id));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (args.length < 3 || !args[0].equalsIgnoreCase("give") || !registry.items().containsKey(args[2])) return;
        event.setCancelled(true);
        Player sender = event.getPlayer();
        if (!sender.hasPermission("flowtail.give")) {
            sender.sendMessage(Component.text("You do not have permission to give FlowTail items."));
            return;
        }
        if (args.length > 4) {
            sender.sendMessage(Component.text("Usage: /give <player|@s> <plugin:item> [count]"));
            return;
        }
        Player target = args[1].equalsIgnoreCase("@s") ? sender : Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Target must be yourself (@s) or an online player."));
            return;
        }
        int count = 1;
        if (args.length == 4) {
            try { count = Integer.parseInt(args[3]); }
            catch (NumberFormatException error) {
                sender.sendMessage(Component.text("Count must be a whole number.")); return;
            }
        }
        final ItemStack stack;
        try { stack = prototypes.create(args[2], count); }
        catch (IllegalArgumentException error) {
            sender.sendMessage(Component.text(error.getMessage())); return;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(Component.text("Gave " + count + " " + args[2] + " to " + target.getName() + "."));
        plugin.getLogger().info("Gave custom item plugin_id=" + args[2].substring(0, args[2].indexOf(':'))
                + " content_id=" + args[2] + " count=" + count + " player=" + target.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTabComplete(TabCompleteEvent event) {
        List<String> candidates = candidates(event.getBuffer());
        if (candidates != null) event.setCompletions(candidates);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) return;
        List<String> candidates = candidates(event.getBuffer());
        if (candidates != null) {
            event.getCompletions().clear();
            event.getCompletions().addAll(candidates);
            event.setHandled(true);
        }
    }

    private List<String> candidates(String rawBuffer) {
        String buffer = rawBuffer.startsWith("/") ? rawBuffer.substring(1) : rawBuffer;
        if (!buffer.startsWith("give ")) return null;
        String[] args = buffer.split("\\s+", -1);
        List<String> candidates = new ArrayList<>();
        if (args.length <= 2) {
            candidates.add("@s");
        } else if (args.length == 3) {
            candidates.addAll(registry.items().keySet());
        } else if (args.length == 4) {
            candidates.add("1"); candidates.add("16"); candidates.add("64");
        }
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(partial))
                .distinct().collect(Collectors.toList());
    }

    private static String namespace(String id) { return id.substring(0, id.indexOf(':')); }
}
