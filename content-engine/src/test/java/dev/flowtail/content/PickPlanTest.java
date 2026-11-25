package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Middle-click on a FlowTail block: reuse what the player has, else spawn (creative only). */
class PickPlanTest {
    private static boolean[] none() { return new boolean[36]; }
    private static boolean[] emptyBar(int... emptySlots) {
        boolean[] out = new boolean[9];
        for (int slot : emptySlots) out[slot] = true;
        return out;
    }

    @Test
    void alreadyHoldingItSelectsNothingNew() {
        boolean[] has = none(); has[4] = true;
        assertEquals(new PickPlan(PickPlan.Kind.SELECT, 4, 4), PickPlan.choose(has, emptyBar(), 4, false));
    }

    @Test
    void itemElsewhereInTheHotbarIsSelected() {
        boolean[] has = none(); has[7] = true;
        assertEquals(new PickPlan(PickPlan.Kind.SELECT, 7, 7), PickPlan.choose(has, emptyBar(), 2, true));
    }

    @Test
    void itemInTheMainInventoryIsSwappedIntoTheSelectedSlotWhenEmptyElseFirstEmptyElseSelected() {
        boolean[] has = none(); has[20] = true;
        assertEquals(new PickPlan(PickPlan.Kind.SWAP, 20, 3), PickPlan.choose(has, emptyBar(3), 3, false));
        assertEquals(new PickPlan(PickPlan.Kind.SWAP, 20, 1), PickPlan.choose(has, emptyBar(1, 8), 3, false));
        assertEquals(new PickPlan(PickPlan.Kind.SWAP, 20, 3), PickPlan.choose(has, emptyBar(), 3, false));
    }

    @Test
    void noMatchSpawnsInCreativeOnly() {
        assertEquals(new PickPlan(PickPlan.Kind.SPAWN, -1, 5), PickPlan.choose(none(), emptyBar(5), 5, true));
        assertEquals(new PickPlan(PickPlan.Kind.SPAWN, -1, 0), PickPlan.choose(none(), emptyBar(0), 5, true));
        assertEquals(new PickPlan(PickPlan.Kind.NOTHING, -1, 5), PickPlan.choose(none(), emptyBar(5), 5, false));
    }

    @Test
    void badInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PickPlan.choose(new boolean[9], emptyBar(), 0, true));
        assertThrows(IllegalArgumentException.class, () -> PickPlan.choose(none(), new boolean[3], 0, true));
        assertThrows(IllegalArgumentException.class, () -> PickPlan.choose(none(), emptyBar(), 9, true));
    }
}
