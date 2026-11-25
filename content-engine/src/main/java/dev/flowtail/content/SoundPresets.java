package dev.flowtail.content;

import java.util.Map;
import java.util.Optional;

/** Maps the compiler's {@code sounds.preset} names to vanilla place-sound keys. No Bukkit types. */
final class SoundPresets {
    private static final Map<String, String> PLACE = Map.of(
            "stone", "minecraft:block.stone.place",
            "wood", "minecraft:block.wood.place",
            "grass", "minecraft:block.grass.place",
            "gravel", "minecraft:block.gravel.place",
            "sand", "minecraft:block.sand.place",
            "metal", "minecraft:block.metal.place",
            "glass", "minecraft:block.glass.place",
            "wool", "minecraft:block.wool.place",
            "snow", "minecraft:block.snow.place",
            "bone", "minecraft:block.bone_block.place");

    private static final Map<String, String> BREAK = Map.of(
            "stone", "minecraft:block.stone.break",
            "wood", "minecraft:block.wood.break",
            "grass", "minecraft:block.grass.break",
            "gravel", "minecraft:block.gravel.break",
            "sand", "minecraft:block.sand.break",
            "metal", "minecraft:block.metal.break",
            "glass", "minecraft:block.glass.break",
            "wool", "minecraft:block.wool.break",
            "snow", "minecraft:block.snow.break",
            "bone", "minecraft:block.bone_block.break");

    private static final Map<String, String> HIT = Map.of(
            "stone", "minecraft:block.stone.hit",
            "wood", "minecraft:block.wood.hit",
            "grass", "minecraft:block.grass.hit",
            "gravel", "minecraft:block.gravel.hit",
            "sand", "minecraft:block.sand.hit",
            "metal", "minecraft:block.metal.hit",
            "glass", "minecraft:block.glass.hit",
            "wool", "minecraft:block.wool.hit",
            "snow", "minecraft:block.snow.hit",
            "bone", "minecraft:block.bone_block.hit");

    private SoundPresets() {}

    /** The "being mined" tap sound for a preset; empty for an absent or unknown preset (then nothing is played). */
    static Optional<String> hitSound(String preset) {
        return preset == null ? Optional.empty() : Optional.ofNullable(HIT.get(preset));
    }

    /** The place sound for a preset; empty for an absent or unknown preset (then nothing is played). */
    static Optional<String> placeSound(String preset) {
        return preset == null ? Optional.empty() : Optional.ofNullable(PLACE.get(preset));
    }

    /** The break sound for a preset; empty for an absent or unknown preset (then nothing is played). */
    static Optional<String> breakSound(String preset) {
        return preset == null ? Optional.empty() : Optional.ofNullable(BREAK.get(preset));
    }
}
