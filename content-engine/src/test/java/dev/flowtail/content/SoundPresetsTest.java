package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundPresetsTest {
    @Test
    void everyPlacePresetHasABreakSound() {
        for (String preset : new String[] {"stone", "wood", "grass", "gravel", "sand", "metal", "glass", "wool", "snow", "bone"}) {
            assertTrue(SoundPresets.placeSound(preset).isPresent(), preset);
            assertTrue(SoundPresets.breakSound(preset).isPresent(), preset);
            assertTrue(SoundPresets.breakSound(preset).get().endsWith(".break"), preset);
        }
    }

    @Test
    void unknownOrAbsentPresetPlaysNothing() {
        assertTrue(SoundPresets.breakSound(null).isEmpty());
        assertTrue(SoundPresets.breakSound("lava").isEmpty());
        assertEquals("minecraft:block.wood.break", SoundPresets.breakSound("wood").orElseThrow());
    }

    @Test
    void everyPresetHasAHitSound() {
        for (String preset : new String[] {"stone", "wood", "grass", "gravel", "sand", "metal", "glass", "wool", "snow", "bone"}) {
            assertTrue(SoundPresets.hitSound(preset).isPresent(), preset);
            assertTrue(SoundPresets.hitSound(preset).get().endsWith(".hit"), preset);
        }
        assertTrue(SoundPresets.hitSound(null).isEmpty());
        assertTrue(SoundPresets.hitSound("nope").isEmpty());
    }
}

