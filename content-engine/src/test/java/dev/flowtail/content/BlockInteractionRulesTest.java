package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** The custom projection has no block-use behavior, so vanilla held-item rules remain available. */
class BlockInteractionRulesTest {
    @Test
    void sneakingWithAnItemSkipsCustomBlockUse() {
        assertTrue(InteractRules.bypassesBlockUse(true, true));
        assertFalse(InteractRules.bypassesBlockUse(true, false));
        assertFalse(InteractRules.bypassesBlockUse(false, true));
    }

    @Test
    void ordinaryInteractableBlocksTakeTheClickUnlessDeniedOrSneaking() {
        assertTrue(InteractRules.vanillaBlockTakesClick(true, false, false));
        assertFalse(InteractRules.vanillaBlockTakesClick(true, true, false));
        assertFalse(InteractRules.vanillaBlockTakesClick(true, false, true));
        assertFalse(InteractRules.vanillaBlockTakesClick(false, false, false));
    }

    @Test
    void adventureAndSpectatorCannotPlace() {
        assertTrue(InteractRules.canBuildInMode(false, false));
        assertFalse(InteractRules.canBuildInMode(true, false));
        assertFalse(InteractRules.canBuildInMode(false, true));
    }
}
