package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flowtail.content.MiningProgress.State;
import dev.flowtail.content.MiningRules.Held;
import dev.flowtail.content.MiningRules.Spec;
import dev.flowtail.content.MiningRules.Tier;
import dev.flowtail.content.MiningRules.Tool;
import org.junit.jupiter.api.Test;

class MiningRulesTest {
    // vanilla stone: hardness 1.5, pickaxe, requires tool. Known vanilla times: hand 7.5s (150 ticks, no drop),
    // wooden pickaxe 1.15s (23 ticks), iron pickaxe 0.4s (8 ticks), diamond pickaxe 0.3s (6 ticks).
    private static final Spec STONE = new Spec(1.5, Tool.PICKAXE, Tier.WOOD, true);

    @Test
    void matchesVanillaStoneTimes() {
        assertEquals(150, MiningRules.breakTicks(STONE, Held.HAND, 0));
        assertEquals(23, MiningRules.breakTicks(STONE, Held.of("WOODEN_PICKAXE"), 0));
        assertEquals(8, MiningRules.breakTicks(STONE, Held.of("minecraft:iron_pickaxe"), 0));
        assertEquals(6, MiningRules.breakTicks(STONE, Held.of("DIAMOND_PICKAXE"), 0));
    }

    @Test
    void efficiencyHelpsOnlyWithTheRightTool() {
        assertTrue(MiningRules.breakTicks(STONE, Held.of("IRON_PICKAXE"), 3) < 8);
        assertEquals(150, MiningRules.breakTicks(STONE, Held.HAND, 5));
        assertEquals(MiningRules.breakTicks(STONE, Held.of("IRON_AXE"), 0), MiningRules.breakTicks(STONE, Held.HAND, 0) );
    }

    @Test
    void harvestNeedsTheRightToolAndTier() {
        Spec ironOre = new Spec(3.0, Tool.PICKAXE, Tier.STONE, true);
        assertFalse(MiningRules.canHarvest(ironOre, Held.HAND));
        assertFalse(MiningRules.canHarvest(ironOre, Held.of("WOODEN_PICKAXE")));
        assertFalse(MiningRules.canHarvest(ironOre, Held.of("GOLDEN_PICKAXE")), "gold is wood level");
        assertFalse(MiningRules.canHarvest(ironOre, Held.of("IRON_AXE")));
        assertTrue(MiningRules.canHarvest(ironOre, Held.of("STONE_PICKAXE")));
        assertTrue(MiningRules.canHarvest(ironOre, Held.of("COPPER_PICKAXE")));
        assertTrue(MiningRules.canHarvest(ironOre, Held.of("NETHERITE_PICKAXE")));
        assertTrue(MiningRules.canHarvest(new Spec(3.0, Tool.PICKAXE, Tier.STONE, false), Held.HAND), "requires_tool=false");
    }

    @Test
    void specialHardness() {
        assertEquals(0, MiningRules.breakTicks(Spec.INSTANT, Held.HAND, 0));
        assertEquals(-1, MiningRules.breakTicks(new Spec(-1, Tool.NONE, Tier.NONE, false), Held.of("NETHERITE_PICKAXE"), 0));
        assertEquals(0, MiningRules.breakTicks(new Spec(0.1, Tool.AXE, Tier.NONE, false), Held.of("DIAMOND_AXE"), 5), "fast enough to be instant");
    }

    @Test
    void blockWithNoMiningSectionIsStoneLike() {
        // Entry 0031 (user decision): no mining section = hardness 1.5. Not instant any more; a pickaxe digs it faster, anything drops it.
        assertEquals(1.5, Spec.DEFAULT.hardness());
        assertEquals(45, MiningRules.breakTicks(Spec.DEFAULT, Held.HAND, 0));          // 1 / (1/1.5/30)
        assertEquals(8, MiningRules.breakTicks(Spec.DEFAULT, Held.of("IRON_PICKAXE"), 0)); // 6/1.5/30 = 0.1333
        assertEquals(45, MiningRules.breakTicks(Spec.DEFAULT, Held.of("IRON_AXE"), 0), "an axe is no faster than a hand on a pickaxe block");
        assertTrue(MiningRules.canHarvest(Spec.DEFAULT, Held.HAND), "requires_tool is false by default");
        assertEquals(0, MiningRules.breakTicks(Spec.INSTANT, Held.HAND, 0), "only an unknown block (plugin removed) still breaks instantly");
    }

    @Test
    void parsesMaterialAndSchemaNames() {
        assertEquals(new Held(Tool.SHEARS, Tier.NONE), Held.of("SHEARS"));
        assertEquals(new Held(Tool.SWORD, Tier.GOLD), Held.of("GOLDEN_SWORD"));
        assertEquals(Held.HAND, Held.of("PAPER"));
        assertEquals(Held.HAND, Held.of("STICK"));
        assertEquals(new Spec(2.0, Tool.AXE, Tier.IRON, true), Spec.of(2.0, "axe", "iron", true));
        assertEquals(1.5, Spec.of(null, null, null, null).hardness(), "a mining section without hardness is stone-like");
        assertEquals(Tier.NONE, Spec.of(1.0, "bogus", "bogus", null).tier());
    }

    @Test
    void progressFinishesWhileSwingingAndCancelsWhenReleasedOrLookingAway() {
        MiningProgress p = new MiningProgress(3);
        assertEquals(State.MINING, p.tick(true, true));
        assertEquals(State.MINING, p.tick(true, false));
        assertEquals(State.DONE, p.tick(true, false));
        assertEquals(1f, p.fraction());

        MiningProgress away = new MiningProgress(100);
        assertEquals(State.CANCELLED, away.tick(false, true));

        MiningProgress released = new MiningProgress(100);
        released.tick(true, true);
        State last = State.MINING;
        for (int i = 0; i < MiningProgress.MAX_IDLE_TICKS + 1; i++) last = released.tick(true, false);
        assertEquals(State.CANCELLED, last);

        MiningProgress holding = new MiningProgress(100);
        for (int i = 0; i < 30; i++) assertEquals(State.MINING, holding.tick(true, i % 3 == 0));
    }

    @Test
    void effectsFireEveryFourthTick() {
        MiningProgress p = new MiningProgress(100);
        int fired = 0;
        for (int i = 0; i < 12; i++) { p.tick(true, true); if (p.effectTick()) fired++; }
        assertEquals(3, fired);
    }

    @Test
    void toolWear() {
        Spec stone = new Spec(1.5, Tool.PICKAXE, Tier.WOOD, true);
        assertEquals(1, MiningRules.toolDamage(stone, Held.of("IRON_PICKAXE")));
        assertEquals(2, MiningRules.toolDamage(stone, Held.of("IRON_SWORD")));
        assertEquals(0, MiningRules.toolDamage(stone, Held.HAND));
        assertEquals(0, MiningRules.toolDamage(Spec.INSTANT, Held.of("IRON_PICKAXE")), "instant blocks do not wear tools");
    }
}

