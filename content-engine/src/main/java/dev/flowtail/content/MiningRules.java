package dev.flowtail.content;

import java.util.Locale;

/**
 * Vanilla-style mining math for FlowTail blocks (P3-04, ADR-027): how many ticks a block takes to break with the
 * item in hand, and whether the break drops anything. Pure logic with no Bukkit types.
 *
 * <p>Formula (vanilla): damage per tick = speed / hardness / (30 if the tool may harvest the block, else 100); the
 * block breaks when the sum reaches 1. Speed is the tool tier's speed when the tool kind matches the block's
 * preferred tool, otherwise 1; efficiency adds level squared plus one when the speed is above 1.
 */
final class MiningRules {
    private MiningRules() {}

    enum Tool { NONE, PICKAXE, AXE, SHOVEL, HOE, SWORD, SHEARS }

    /** Harvest level (what a tool may mine) and mining speed per tier, as in vanilla. */
    enum Tier {
        NONE(0, 1f), WOOD(1, 2f), GOLD(1, 12f), STONE(2, 4f), COPPER(2, 5f), IRON(3, 6f), DIAMOND(4, 8f), NETHERITE(5, 9f);
        final int level; final float speed;
        Tier(int level, float speed) { this.level = level; this.speed = speed; }
    }

    /** What the player holds. Bare hand and non-tools are {@code NONE/NONE}. */
    record Held(Tool tool, Tier tier) {
        static final Held HAND = new Held(Tool.NONE, Tier.NONE);

        /** Reads a Bukkit/Minecraft material name such as {@code IRON_PICKAXE}, {@code minecraft:golden_axe} or {@code SHEARS}. */
        static Held of(String materialName) {
            String name = materialName.toUpperCase(Locale.ROOT);
            if (name.startsWith("MINECRAFT:")) name = name.substring("MINECRAFT:".length());
            if (name.equals("SHEARS")) return new Held(Tool.SHEARS, Tier.NONE);
            Tool tool = name.endsWith("_PICKAXE") ? Tool.PICKAXE : name.endsWith("_AXE") ? Tool.AXE
                    : name.endsWith("_SHOVEL") ? Tool.SHOVEL : name.endsWith("_HOE") ? Tool.HOE
                    : name.endsWith("_SWORD") ? Tool.SWORD : Tool.NONE;
            if (tool == Tool.NONE) return HAND;
            Tier tier = name.startsWith("WOODEN_") ? Tier.WOOD : name.startsWith("STONE_") ? Tier.STONE
                    : name.startsWith("COPPER_") ? Tier.COPPER : name.startsWith("IRON_") ? Tier.IRON
                    : name.startsWith("DIAMOND_") ? Tier.DIAMOND : name.startsWith("NETHERITE_") ? Tier.NETHERITE
                    : name.startsWith("GOLDEN_") ? Tier.GOLD : Tier.NONE;
            return new Held(tool, tier);
        }
    }

    /** A block's {@code mining} section after defaults. {@code hardness} -1 means unbreakable, 0 means instant, omitted means 1.5. */
    record Spec(double hardness, Tool tool, Tier tier, boolean requiresTool) {
        /** Instant break, always drops: only for the placeholder of a block whose plugin is gone. */
        static final Spec INSTANT = new Spec(0, Tool.NONE, Tier.NONE, false);

        /** Hardness of a block with no {@code mining} section, or a section without {@code hardness}: stone-like (user decision, Entry 0031). */
        static final double DEFAULT_HARDNESS = 1.5;

        /** The defaults: hardness 1.5, a pickaxe digs it faster, any tool or the bare hand still gets the drop. */
        static final Spec DEFAULT = new Spec(DEFAULT_HARDNESS, Tool.PICKAXE, Tier.NONE, false);

        /** Builds a spec from raw schema values; any may be null. Unknown names mean "none" (the compiler already rejects them). */
        static Spec of(Double hardness, String tool, String tier, Boolean requiresTool) {
            return new Spec(hardness == null ? DEFAULT_HARDNESS : hardness, parseTool(tool), parseTier(tier), requiresTool != null && requiresTool);
        }

        private static Tool parseTool(String name) {
            if (name == null) return Tool.NONE;
            try { return Tool.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException error) { return Tool.NONE; }
        }

        private static Tier parseTier(String name) {
            if (name == null) return Tier.NONE;
            String key = name.toUpperCase(Locale.ROOT);
            if (key.equals("WOODEN")) key = "WOOD";
            if (key.equals("GOLDEN")) key = "GOLD";
            try { return Tier.valueOf(key); } catch (IllegalArgumentException error) { return Tier.NONE; }
        }
    }

    /** True when breaking with {@code held} yields the block's drop. */
    static boolean canHarvest(Spec spec, Held held) {
        if (!spec.requiresTool() || spec.tool() == Tool.NONE) return true;
        return held.tool() == spec.tool() && held.tier().level >= spec.tier().level;
    }

    /** Ticks to break: -1 unbreakable, 0 instant, otherwise 1 or more. */
    static int breakTicks(Spec spec, Held held, int efficiencyLevel) {
        if (spec.hardness() < 0) return -1;
        if (spec.hardness() == 0) return 0;
        double speed = 1.0;
        if (spec.tool() != Tool.NONE && held.tool() == spec.tool()) speed = held.tier().speed;
        if (speed > 1.0 && efficiencyLevel > 0) speed += efficiencyLevel * efficiencyLevel + 1;
        double damage = speed / spec.hardness() / (canHarvest(spec, held) ? 30.0 : 100.0);
        if (damage >= 1.0) return 0;
        // The small subtraction keeps floating point noise (45.00000000000001) from costing a whole extra tick.
        return (int) Math.ceil(1.0 / damage - 1e-6);
    }

    /** Durability the held tool loses for one finished break, as in vanilla: swords 2, other tools 1, nothing for instant blocks, hands or non-tools. */
    static int toolDamage(Spec spec, Held held) {
        if (spec.hardness() <= 0 || held.tool() == Tool.NONE) return 0;
        return held.tool() == Tool.SWORD ? 2 : 1;
    }
}

