package dev.flowtail.content;

import java.util.Set;

/**
 * Block materials that older FlowTail builds used at positions owned by a saved FlowTail id record.
 * They are recognized only to migrate those recorded positions to the barrier hitbox. They are never
 * selected for new placements and unrecorded vanilla blocks are never changed.
 */
final class LegacyShapeCarriers {
    private LegacyShapeCarriers() {}

    static Set<String> allMaterials() {
        return Set.of("barrier", "note_block", "oak_slab", "stone_slab", "waxed_cut_copper_slab",
                "oak_stairs", "stone_stairs", "waxed_cut_copper_stairs", "oak_trapdoor", "oak_button",
                "stone_button", "oak_pressure_plate", "stone_pressure_plate", "white_carpet", "light", "oak_shelf");
    }
}
