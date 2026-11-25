package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flowtail.content.ProjectionSpec.Face;
import org.junit.jupiter.api.Test;

class ProjectionSpecTest {
    @Test
    void displayFillsTheBlockInsteadOfShrinkingIt() {
        // Regression: FIXED display context halved every custom block. The scale must be a hair above 1, never below.
        assertTrue(ProjectionSpec.DISPLAY_SCALE > 1.0f && ProjectionSpec.DISPLAY_SCALE < 1.01f);
    }

    @Test
    void interactionBoxIsInsideTheBlockSoTheBarrierWinsTheClick() {
        assertTrue(ProjectionSpec.INTERACTION_SIZE < 1.0f && ProjectionSpec.INTERACTION_SIZE > 0.9f);
    }

    @Test
    void interactionStandsOnTheBlockFloorNotInItsMiddle() {
        // Regression: an Interaction box grows upward from the entity's feet; spawning at y+0.5 shifted it up half a block.
        assertEquals(0.0, ProjectionSpec.INTERACTION_OFFSET[1]);
        assertEquals(0.5, ProjectionSpec.INTERACTION_OFFSET[0]);
        assertEquals(0.5, ProjectionSpec.INTERACTION_OFFSET[2]);
        assertEquals(0.5, ProjectionSpec.DISPLAY_OFFSET[1]);
    }

    @Test
    void clickPositionMapsToTheFaceItLandedOn() {
        // relative to the entity's feet: x/z centred on 0, y from 0 (floor) to 1 (ceiling)
        assertEquals(Face.UP, ProjectionSpec.faceOfInteractionClick(0.1, 0.98, -0.2));
        assertEquals(Face.DOWN, ProjectionSpec.faceOfInteractionClick(0.1, 0.02, 0.2));
        assertEquals(Face.EAST, ProjectionSpec.faceOfInteractionClick(0.49, 0.5, 0.1));
        assertEquals(Face.WEST, ProjectionSpec.faceOfInteractionClick(-0.49, 0.6, 0.1));
        assertEquals(Face.SOUTH, ProjectionSpec.faceOfInteractionClick(0.1, 0.5, 0.49));
        assertEquals(Face.NORTH, ProjectionSpec.faceOfInteractionClick(0.1, 0.4, -0.49));
    }

    @Test
    void tiesAreDeterministic() {
        assertEquals(Face.UP, ProjectionSpec.faceOfInteractionClick(0.5, 1.0, 0.5)); // vertical beats east/west and north/south
        assertEquals(Face.EAST, ProjectionSpec.faceOfInteractionClick(0.5, 0.5, 0.5)); // x beats z when y is centred
        assertEquals(Face.UP, ProjectionSpec.faceOfInteractionClick(0, 0.5, 0)); // dead centre: vertical, never an exception
    }

    @Test
    void stateKeyCarriesTheVariantSoAStateChangeMarksTheDisplayStale() {
        String closed = ProjectionSpec.stateKey("a:hatch", true, "bottom_closed");
        assertFalse(ProjectionSpec.displayIsStale(closed, "a:hatch", true, "bottom_closed"));
        assertTrue(ProjectionSpec.displayIsStale(closed, "a:hatch", true, "open_north"));
        assertTrue(ProjectionSpec.displayIsStale(closed, "a:hatch", true), "a cube-style key never matches a shaped display");
        assertEquals(ProjectionSpec.stateKey("a:x", true), ProjectionSpec.stateKey("a:x", true, null));
        assertEquals(ProjectionSpec.stateKey("a:x", false), ProjectionSpec.stateKey("a:x", false, "bottom"), "a placeholder has no variant");
        assertEquals(3, ProjectionSpec.LAYOUT, "layout 3 = shaped blocks; bump again when geometry changes");
    }

    @Test
    void shapedBlocksAreNotScaledTheirModelsAreAlreadyGrown() {
        assertEquals(1.0f, ProjectionSpec.displayScale(true));
        assertEquals(ProjectionSpec.DISPLAY_SCALE, ProjectionSpec.displayScale(false));
    }

    @Test
    void stateKeyTracksLayoutIdAndPlaceholder() {
        String known = ProjectionSpec.stateKey("a:x", true);
        String missing = ProjectionSpec.stateKey("a:x", false);
        assertNotEquals(known, missing);
        assertTrue(known.startsWith("v" + ProjectionSpec.LAYOUT + ":"));
        assertTrue(missing.endsWith("|missing"));
        assertFalse(ProjectionSpec.displayIsStale(known, "a:x", true));
        assertTrue(ProjectionSpec.displayIsStale(missing, "a:x", true), "plugin re-added: placeholder must be rebuilt");
        assertTrue(ProjectionSpec.displayIsStale(known, "a:x", false), "plugin removed: real model must become placeholder");
        assertTrue(ProjectionSpec.displayIsStale(null, "a:x", true), "projection made before state markers existed");
        assertTrue(ProjectionSpec.displayIsStale("v1:a:x", "a:x", true), "old layout must be rebuilt");
        assertTrue(ProjectionSpec.displayIsStale("v2:a:x", "a:x", true), "layout 2 (before shapes) must be rebuilt");
        assertTrue(ProjectionSpec.displayIsStale(known, "a:y", true));
    }

    @Test
    void ordinaryChunksCostNothingOnLoad() {
        assertFalse(ProjectionSpec.chunkNeedsCheck(0, 0), "no records: no entity scan, no forced entity load");
        assertTrue(ProjectionSpec.chunkNeedsCheck(1, 0));
        assertTrue(ProjectionSpec.chunkNeedsCheck(0, 2), "unreadable lines still need the careful path");
    }
}
