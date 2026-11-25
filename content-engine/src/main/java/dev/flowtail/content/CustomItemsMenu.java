package dev.flowtail.content;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The Custom Items chest menu: every FlowTail item and block, paged, click to take. Opened from the pause-screen
 * dialog's Custom Items button (a custom click) or with {@code /flowtail}. Who may use it is {@code menu.custom-items.access}.
 */
final class CustomItemsMenu implements Listener {
    static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS = 45, CLOSE = 49, NEXT = 53;

    private static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final FlowTailContentPlugin plugin;
    private final ItemPrototypes prototypes;
    private final ContentSettings.MenuSettings menu;
    private final List<String> ids;

    CustomItemsMenu(FlowTailContentPlugin plugin, ItemPrototypes prototypes, ContentSettings.MenuSettings menu) {
        this.plugin = plugin; this.prototypes = prototypes; this.menu = menu;
        this.ids = prototypes.ids().stream().sorted().toList();
    }

    // ---- opening ----

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        if (!MenuDialogs.CUSTOM_ITEMS_CLICK.equals(event.getIdentifier())) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Runnable open = () -> openIfAllowed(player);
        if (Bukkit.isPrimaryThread()) open.run(); else Bukkit.getScheduler().runTask(plugin, open);
    }

    /** {@code /flowtail}, {@code /flowtail items} and {@code /flowtail menu}: a fallback when the pause button is unavailable. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().trim().split("\\s+");
        if (!args[0].equalsIgnoreCase("/flowtail")) return;
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("items") && !args[1].equalsIgnoreCase("menu"))) return;
        event.setCancelled(true);
        openIfAllowed(event.getPlayer());
    }

    private void openIfAllowed(Player player) {
        if (!allowed(player)) {
            player.sendMessage(Component.text(switch (menu.itemsAccess()) {
                case "creative" -> menu.itemsLabel() + " is only available in creative mode.";
                case "op" -> menu.itemsLabel() + " is only available to operators.";
                default -> menu.itemsLabel() + " is not available.";
            }, NamedTextColor.RED));
            return;
        }
        open(player, 0);
    }

    private boolean allowed(Player player) {
        return MenuStyle.allowed(menu.itemsAccess(), player.getGameMode() == GameMode.CREATIVE, player.isOp());
    }

    private void open(Player player, int requestedPage) {
        int pages = MenuStyle.pageCount(ids.size(), ITEMS_PER_PAGE);
        int page = MenuStyle.clampPage(requestedPage, pages);
        Holder holder = new Holder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, MenuComponents.gradientLabel(menu.itemsTitle(), menu));
        holder.inventory = inventory;
        int start = page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE && start + slot < ids.size(); slot++) {
            String id = ids.get(start + slot);
            ItemStack display = prototypes.create(id);
            ItemMeta meta = display.getItemMeta();
            meta.lore(List.of(plain(id, NamedTextColor.DARK_GRAY), plain("Click: 1   Shift-click: full stack", NamedTextColor.GRAY)));
            display.setItemMeta(meta);
            inventory.setItem(slot, display);
        }
        if (page > 0) inventory.setItem(PREVIOUS, button(Material.ARROW, "Previous page", null));
        inventory.setItem(CLOSE, button(Material.BARRIER, "Close", "Page " + (page + 1) + " of " + pages));
        if (page < pages - 1) inventory.setItem(NEXT, button(Material.ARROW, "Next page", null));
        player.openInventory(inventory);
        plugin.getLogger().info("Custom items menu opened player=" + player.getUniqueId() + " page=" + (page + 1) + "/" + pages);
    }

    // ---- clicking ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) return;
        if (!allowed(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        int slot = event.getSlot();
        if (slot < ITEMS_PER_PAGE) {
            int index = holder.page * ITEMS_PER_PAGE + slot;
            if (index < ids.size()) give(player, ids.get(index), event.isShiftClick() || event.isRightClick());
        } else if (slot == PREVIOUS && holder.page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.page - 1));
        } else if (slot == NEXT && holder.page < MenuStyle.pageCount(ids.size(), ITEMS_PER_PAGE) - 1) {
            Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.page + 1));
        } else if (slot == CLOSE) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }

    private void give(Player player, String id, boolean fullStack) {
        int amount = fullStack ? prototypes.maxStackSize(id) : 1;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(prototypes.create(id, amount));
        for (ItemStack rest : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
        plugin.getLogger().info("Menu gave custom item plugin_id=" + id.substring(0, id.indexOf(':')) + " content_id=" + id
                + " count=" + amount + " player=" + player.getUniqueId());
    }

    private static Component plain(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name, NamedTextColor.WHITE));
        if (lore != null) meta.lore(new ArrayList<>(List.of(plain(lore, NamedTextColor.GRAY))));
        stack.setItemMeta(meta);
        return stack;
    }
}
