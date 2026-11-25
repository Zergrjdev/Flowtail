package dev.flowtail.content.api;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player interacted with a FlowTail block. This is the supported way to react to clicks on the interaction entity.
 *
 * <p>Not fired for sneaking players who hold an item (vanilla: sneaking skips block interaction), nor for blocks
 * whose plugin is not installed. Cancelling stops block placement against the block and any use of the held item
 * for this click. Entity interactions currently report {@link BlockFace#UP}, because Paper's entity interaction
 * event does not supply a clicked block face. Fired once per hand, main thread.
 */
public final class FlowTailBlockInteractEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final String contentId;
    private final EquipmentSlot hand;
    private final ItemStack item;
    private final BlockFace face;
    private boolean cancelled;

    public FlowTailBlockInteractEvent(@NotNull Player player, @NotNull Block block, @NotNull String contentId,
                                      @NotNull EquipmentSlot hand, @Nullable ItemStack item, @NotNull BlockFace face) {
        super(player);
        this.block = block; this.contentId = contentId; this.hand = hand; this.item = item; this.face = face;
    }

    /** The clicked block. */
    public @NotNull Block getBlock() { return block; }

    /** The clicked block's content id, e.g. {@code example:rock}. */
    public @NotNull String getContentId() { return contentId; }

    public @NotNull EquipmentSlot getHand() { return hand; }

    /** The item in the clicking hand, or {@code null} when the hand is empty. */
    public @Nullable ItemStack getItem() { return item; }

    public @NotNull BlockFace getFace() { return face; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
