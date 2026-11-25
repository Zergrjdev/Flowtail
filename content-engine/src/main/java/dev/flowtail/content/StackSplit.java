package dev.flowtail.content;

import java.util.ArrayList;
import java.util.List;

/** Splits a /give count into stack sizes like vanilla does. Pure logic, no Bukkit types. */
final class StackSplit {
    /** Vanilla refuses more than 100 stacks in one /give. */
    static final int MAX_STACKS = 100;

    private StackSplit() {}

    /** {@code split(150, 64)} is {@code [64, 64, 22]}. Throws with a plain-English message when the count is not allowed. */
    static List<Integer> split(int count, int maxStack) {
        if (maxStack < 1) throw new IllegalArgumentException("max stack size must be at least 1");
        if (count < 1) throw new IllegalArgumentException("count must be at least 1");
        int stacks = (count + maxStack - 1) / maxStack;
        if (stacks > MAX_STACKS) {
            throw new IllegalArgumentException("count is too large: at most " + MAX_STACKS * maxStack + " of this item at once");
        }
        List<Integer> sizes = new ArrayList<>();
        for (int left = count; left > 0; left -= maxStack) sizes.add(Math.min(left, maxStack));
        return sizes;
    }
}
