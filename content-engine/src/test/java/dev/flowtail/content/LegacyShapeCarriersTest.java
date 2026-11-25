package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyShapeCarriersTest {
    @Test
    void historicalVanillaStatesAreRecognizedOnlyForRecordedWorldMigration() {
        var oldMaterials = LegacyShapeCarriers.allMaterials();
        assertTrue(oldMaterials.contains("note_block"));
        assertTrue(oldMaterials.contains("oak_shelf"));
        assertTrue(oldMaterials.contains("stone_slab"));
        assertTrue(oldMaterials.contains("waxed_cut_copper_stairs"));
        assertTrue(oldMaterials.contains("barrier"));
        assertEquals(16, oldMaterials.size());
    }
}
