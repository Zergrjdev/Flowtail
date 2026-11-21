package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StackSizePolicyTest {
    @Test
    void compilerDefaultOnDamageableSwordKeepsBaseMaxWithoutNoise() {
        // iron_sword: max stack 1, durability 250; the compiler wrote the implicit 64.
        StackSizePolicy.Result result = StackSizePolicy.resolve("example:ruby_sword", 64, 1, 250);
        assertEquals(1, result.effective());
        assertFalse(result.overrideComponent());
        assertNull(result.warning());
    }

    @Test
    void explicitStackSizeOnDamageableBaseIsIgnoredWithWarning() {
        StackSizePolicy.Result result = StackSizePolicy.resolve("example:x", 16, 1, 250);
        assertEquals(1, result.effective());
        assertFalse(result.overrideComponent());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("example:x"));
    }

    @Test
    void stackableBaseUsesRequestedSize() {
        StackSizePolicy.Result result = StackSizePolicy.resolve("example:ruby", 16, 64, 0);
        assertEquals(16, result.effective());
        assertTrue(result.overrideComponent());
        assertNull(result.warning());
    }

    @Test
    void stackableBaseAtItsOwnDefaultWritesNoComponent() {
        StackSizePolicy.Result result = StackSizePolicy.resolve("example:ruby", 64, 64, 0);
        assertEquals(64, result.effective());
        assertFalse(result.overrideComponent());
    }

    @Test
    void outOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> StackSizePolicy.resolve("example:x", 0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> StackSizePolicy.resolve("example:x", 100, 64, 0));
    }
}
