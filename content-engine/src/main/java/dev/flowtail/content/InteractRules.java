package dev.flowtail.content;

/** Vanilla click rules that decide whether a held block item is placed. Pure logic, no Bukkit types. */
final class InteractRules {
    private InteractRules() {}

    /** Vanilla: a sneaking player holding an item skips the clicked block's own interaction. */
    static boolean bypassesBlockUse(boolean sneaking, boolean holdingItem) {
        return sneaking && holdingItem;
    }

    /**
     * True when an ordinary (non-FlowTail) block under the cursor takes the click, which stops a held FlowTail block
     * item from being placed (chests, doors, crafting tables...). FlowTail blocks never take it: they have no
     * carrier interaction of their own, so a block item can always be placed against them.
     */
    static boolean vanillaBlockTakesClick(boolean vanillaInteractable, boolean useDenied, boolean sneakingWithItem) {
        return vanillaInteractable && !useDenied && !sneakingWithItem;
    }

    /** Adventure and spectator players cannot place blocks. */
    static boolean canBuildInMode(boolean adventure, boolean spectator) {
        return !adventure && !spectator;
    }
}
