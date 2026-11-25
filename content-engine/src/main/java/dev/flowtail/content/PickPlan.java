package dev.flowtail.content;

/**
 * Decides what middle-click ("pick block") does for a FlowTail block, mirroring vanilla: use the matching item the
 * player already has (select it in the hotbar, or swap it in from the main inventory); in creative with no match,
 * spawn a fresh one. Pure logic, no Bukkit types.
 *
 * <p>Slots follow the Bukkit player inventory: 0..8 hotbar, 9..35 main inventory.
 */
record PickPlan(Kind kind, int from, int to) {
    enum Kind { SELECT, SWAP, SPAWN, NOTHING }

    static PickPlan choose(boolean[] matches, boolean[] hotbarEmpty, int selected, boolean creative) {
        if (matches.length != 36) throw new IllegalArgumentException("matches must cover slots 0..35");
        if (hotbarEmpty.length != 9) throw new IllegalArgumentException("hotbarEmpty must cover slots 0..8");
        if (selected < 0 || selected > 8) throw new IllegalArgumentException("selected must be 0..8");
        if (matches[selected]) return new PickPlan(Kind.SELECT, selected, selected);
        for (int slot = 0; slot < 9; slot++) if (matches[slot]) return new PickPlan(Kind.SELECT, slot, slot);
        int target = suitableHotbarSlot(hotbarEmpty, selected);
        for (int slot = 9; slot < 36; slot++) if (matches[slot]) return new PickPlan(Kind.SWAP, slot, target);
        return creative ? new PickPlan(Kind.SPAWN, -1, target) : new PickPlan(Kind.NOTHING, -1, selected);
    }

    /** The selected slot if it is empty, else the first empty hotbar slot, else the selected slot (replaced). */
    static int suitableHotbarSlot(boolean[] hotbarEmpty, int selected) {
        if (hotbarEmpty[selected]) return selected;
        for (int slot = 0; slot < 9; slot++) if (hotbarEmpty[slot]) return slot;
        return selected;
    }
}
