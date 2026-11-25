package dev.flowtail.content;

/**
 * Geometry and identity rules of a block projection (ADR-024, ADR-025). Pure logic with no Bukkit types, so it is
 * unit-tested without a server.
 *
 * <p>A projection is three things at one block position: a barrier (collision/selection), an item display (the
 * visible cube) and an interaction entity (click relay). The numbers below decide how they line up.
 */
final class ProjectionSpec {
    private ProjectionSpec() {}

    /**
     * The display is drawn with the item display context {@code NONE}, which applies no display transform, so a
     * cube model fills exactly one block. ({@code FIXED} would apply the vanilla block model's item-frame transform,
     * which scales it to half size: the "tiny block" bug found 2026-11-22.)
     *
     * <p>The cube is a hair larger than the block so its faces never z-fight with a neighbouring block's faces.
     */
    static final float DISPLAY_SCALE = 1.002f;

    /**
     * Side of the interaction entity's box. Deliberately a little smaller than the block: the client picks the
     * entity only when it is strictly closer than the block, so with this size the barrier is always the click
     * target, and vanilla placement against a FlowTail block (cobblestone, stairs, torches...) behaves exactly like
     * against stone. The entity still receives clicks the barrier cannot take (see {@code BlockListener}).
     */
    static final float INTERACTION_SIZE = 0.98f;

    /** Display entities are positioned at the block centre (the model is centred on the entity). */
    static final double[] DISPLAY_OFFSET = {0.5, 0.5, 0.5};

    /**
     * Interaction entities are positioned at the block <em>base</em> centre: their box grows upward from the
     * entity's feet and is centred horizontally. (Spawning them at the block centre shifted the box up half a block.)
     */
    static final double[] INTERACTION_OFFSET = {0.5, 0.0, 0.5};

    /** Which side of a block was clicked. Mirrors Bukkit's {@code BlockFace} without depending on it. */
    enum Face { UP, DOWN, NORTH, SOUTH, EAST, WEST }

    /**
     * The face of the block a click landed on, from the click position relative to the interaction entity's feet
     * (Bukkit's {@code PlayerInteractAtEntityEvent#getClickedPosition}). The component farthest from the centre
     * wins; on an exact tie vertical beats east/west beats north/south, so the result is deterministic.
     */
    static Face faceOfInteractionClick(double relX, double relY, double relZ) {
        double x = relX, y = relY - INTERACTION_OFFSET_Y_TO_CENTER, z = relZ;
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ay >= ax && ay >= az) return y >= 0 ? Face.UP : Face.DOWN;
        if (ax >= az) return x >= 0 ? Face.EAST : Face.WEST;
        return z >= 0 ? Face.SOUTH : Face.NORTH;
    }

    /** From an interaction entity's feet up to the block centre. */
    private static final double INTERACTION_OFFSET_Y_TO_CENTER = 0.5;

    /**
     * Version of the projection geometry above. Bump it whenever a number or entity setup changes: chunk load
     * rebuilds every projection whose stored state carries a different layout, so existing worlds heal themselves.
     * Layout 1 was the original build (FIXED display, interaction box shifted up half a block). Layout 2 was the first cube projection. Layout 3 adds shaped blocks (ADR-028): the state key carries the model variant, and shaped blocks are drawn at scale 1 with the grown geometry baked into their models.
     */
    static final int LAYOUT = 3;

    /**
     * What a projection's display currently shows, stored on the entity so a later chunk load can tell when it is
     * stale: layout version plus the id while the plugin is installed, {@code id|missing} while it is not.
     */
    static String stateKey(String id, boolean known) { return stateKey(id, known, null); }

    /** As above, plus the model variant of a shaped block ({@code null} for a cube), so a state change marks the display stale. */
    static String stateKey(String id, boolean known, String variant) {
        return "v" + LAYOUT + ":" + (known ? id : id + "|missing") + (variant == null || !known ? "" : "#" + variant);
    }

    /** True when an existing display must be refreshed to show what {@code stateKey(id, known)} says. */
    static boolean displayIsStale(String storedState, String id, boolean known) {
        return displayIsStale(storedState, id, known, null);
    }

    static boolean displayIsStale(String storedState, String id, boolean known, String variant) {
        return !stateKey(id, known, variant).equals(storedState);
    }

    /** Interaction box of a plant: roughly a flower. Real-carrier shapes need none; the carrier is the click target. */
    static final float PLANT_BOX_WIDTH = 0.6f, PLANT_BOX_HEIGHT = 0.8f;

    /** Cubes are drawn scaled to cover the barrier; shaped blocks carry their grown geometry in the model (scale 1). */
    static float displayScale(boolean shaped) { return shaped ? 1.0f : DISPLAY_SCALE; }

    /**
     * Whether a chunk that just loaded needs the projection check at all. A chunk with no records (the vast
     * majority) must cost nothing: no entity scan, no forced entity load. Unreadable lines count as work because
     * their neighbours' entities must be protected from the orphan sweep, not skipped.
     */
    static boolean chunkNeedsCheck(int parsedRecords, int unreadableLines) {
        return parsedRecords > 0 || unreadableLines > 0;
    }
}
