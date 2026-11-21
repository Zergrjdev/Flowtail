package dev.flowtail.content;

/**
 * Decides which {@code max_stack_size} a FlowTail item carries.
 *
 * <p>Pure logic with no Bukkit types so it can be unit-tested without a server. The compiler currently writes
 * {@code stack_size: 64} for every item that does not set one, including damageable bases such as swords, and a
 * damageable item must not be stackable. So a damageable base always keeps its own maximum.
 */
final class StackSizePolicy {
    /** The compiler's implicit default (see compiler/ftc/build.py); it is not an author choice. */
    static final int COMPILER_DEFAULT = 64;

    /**
     * @param effective         the maximum stack size the finished stack has
     * @param overrideComponent whether a max_stack_size component must be written (false = base item default)
     * @param warning           plain-English problem to log once, or null
     */
    record Result(int effective, boolean overrideComponent, String warning) {}

    private StackSizePolicy() {}

    static Result resolve(String id, int requested, int baseMax, int baseDurability) {
        if (requested < 1 || requested > 99) {
            throw new IllegalArgumentException(id + ": stack_size must be 1 to 99 but is " + requested);
        }
        if (baseDurability > 0) {
            String warning = requested != baseMax && requested != COMPILER_DEFAULT
                    ? id + ": stack_size " + requested + " ignored because its base item is damageable (max stack "
                    + baseMax + ")"
                    : null;
            return new Result(baseMax, false, warning);
        }
        return new Result(requested, requested != baseMax, null);
    }
}
