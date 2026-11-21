package dev.flowtail.content;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;

/** Re-expands a recognized stack when a creative client sends it back to the server. */
final class CreativeItemListener implements Listener {
    private final FlowTailContentPlugin plugin;
    private final ItemPrototypes prototypes;

    CreativeItemListener(FlowTailContentPlugin plugin, ItemPrototypes prototypes) {
        this.plugin = plugin; this.prototypes = prototypes;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        var returned = event.getCursor();
        var id = prototypes.idOf(returned);
        if (id.isPresent()) {
            event.setCursor(prototypes.expand(returned));
            plugin.getLogger().info("Creative item returned and recognized plugin_id="
                    + id.get().substring(0, id.get().indexOf(':')) + " content_id=" + id.get());
        }
    }
}
