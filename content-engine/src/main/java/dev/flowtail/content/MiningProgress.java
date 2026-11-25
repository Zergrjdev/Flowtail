package dev.flowtail.content;

/**
 * One player's progress on one block. The server cannot see the client's dig progress, so "still digging" is
 * judged from arm swings: a client that holds the attack button swings about every 3 ticks. Pure logic, no Bukkit.
 */
final class MiningProgress {
    enum State { MINING, DONE, CANCELLED }

    /** Ticks without a swing before the player counts as having let go. */
    static final int MAX_IDLE_TICKS = 7;

    private final int ticksNeeded;
    private int progress;
    private int idle;

    MiningProgress(int ticksNeeded) {
        if (ticksNeeded < 1) throw new IllegalArgumentException("ticksNeeded must be at least 1");
        this.ticksNeeded = ticksNeeded;
    }

    /** Advance one server tick. {@code targeting}: the player still looks at the block. {@code swung}: a swing arrived this tick. */
    State tick(boolean targeting, boolean swung) {
        if (!targeting) return State.CANCELLED;
        idle = swung ? 0 : idle + 1;
        if (idle > MAX_IDLE_TICKS) return State.CANCELLED;
        progress++;
        return progress >= ticksNeeded ? State.DONE : State.MINING;
    }

    /** Ticks of mining so far; the effects (tap sound, particles) fire every {@link #EFFECT_INTERVAL} of them. */
    int ticks() { return progress; }

    /** True on the ticks that should play the tap sound and spawn particles (vanilla taps about every 4 ticks). */
    boolean effectTick() { return progress > 0 && progress % EFFECT_INTERVAL == 0; }

    static final int EFFECT_INTERVAL = 4;

    /** 0..1 progress, for any progress display. */
    float fraction() { return Math.min(1f, (float) progress / ticksNeeded); }
}
