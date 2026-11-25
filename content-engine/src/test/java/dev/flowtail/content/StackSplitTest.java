package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StackSplitTest {
    @Test
    void splitsLikeVanilla() {
        assertEquals(List.of(64, 64, 22), StackSplit.split(150, 64));
        assertEquals(List.of(1, 1), StackSplit.split(2, 1)); // swords: two separate stacks
        assertEquals(List.of(16), StackSplit.split(16, 64));
        assertEquals(List.of(64), StackSplit.split(64, 64));
    }

    @Test
    void rejectsBadCounts() {
        assertThrows(IllegalArgumentException.class, () -> StackSplit.split(0, 64));
        assertThrows(IllegalArgumentException.class, () -> StackSplit.split(-3, 64));
        assertThrows(IllegalArgumentException.class, () -> StackSplit.split(101, 1));
        assertEquals(100, StackSplit.split(100, 1).size());
    }
}
