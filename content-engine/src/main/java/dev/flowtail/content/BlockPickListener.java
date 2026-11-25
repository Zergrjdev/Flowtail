package dev.flowtail.content;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Middle-click on a FlowTail block gives the FlowTail item, never the carrier's vanilla item (a plain note block).
 * Vanilla would hand out the block's own item, so the event is cancelled and the same hotbar rules are applied here
 * ({@link PickPlan}). Needs Paper's {@code PlayerPickBlockEvent}.
 */
final class BlockPickListener implements Listener {
    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final ItemPrototypes prototypes;
    private final BlockManager blocks;

    BlockPickListener(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes prototypes, BlockManager blocks) {
        this.plugin = plugin; this.registry = registry; this.prototypes = prototypes; this.blocks = blocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        Block block = event.getBlock();
        Optional<String> tracked = blocks.idAt(block);
        if (tracked.isEmpty()) return;
        event.setCancelled(true);
        String id = tracked.get();
        Player player = event.getPlayer();
        if (!registry.blocks().containsKey(id)) {
            player.sendMessage(Component.text("That block belongs to a plugin that is not installed, so there is no item for it."));
            return;
        }
        if (!prototypes.has(id)) {
            player.sendMessage(Component.text("That block has no item (item.auto is false), so there is nothing to pick."));
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean[] matches = new boolean[36];
        boolean[] hotbarEmpty = new boolean[9];
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            matches[slot] = stack != null && prototypes.idOf(stack).filter(id::equals).isPresent();
            if (slot < 9) hotbarEmpty[slot] = stack == null || stack.getType().isAir();
        }
        PickPlan plan = PickPlan.choose(matches, hotbarEmpty, inventory.getHeldItemSlot(),
                player.getGameMode() == GameMode.CREATIVE);
        switch (plan.kind()) {
            case SELECT -> inventory.setHeldItemSlot(plan.to());
            case SWAP -> {
                ItemStack picked = inventory.getItem(plan.from());
                ItemStack displaced = inventory.getItem(plan.to());
                inventory.setItem(plan.to(), picked);
                inventory.setItem(plan.from(), displaced);
                inventory.setHeldItemSlot(plan.to());
            }
            case SPAWN -> {
                inventory.setItem(plan.to(), prototypes.create(id));
                inventory.setHeldItemSlot(plan.to());
            }
            case NOTHING -> { return; }
        }
        plugin.getLogger().info("Picked custom block plugin_id=" + id.substring(0, id.indexOf(':')) + " content_id=" + id
                + " mode=" + plan.kind().name().toLowerCase(java.util.Locale.ROOT) + " player=" + player.getUniqueId());
    }
}
