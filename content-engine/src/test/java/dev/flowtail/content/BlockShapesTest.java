package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flowtail.content.BlockShapes.Dir;
import dev.flowtail.content.BlockShapes.Box;
import dev.flowtail.content.BlockShapes.Geometry;
import dev.flowtail.content.BlockShapes.PlaceContext;
import dev.flowtail.content.BlockShapes.StairAt;
import dev.flowtail.content.BlockShapes.Support;
import dev.flowtail.content.BlockShapes.Surroundings;
import dev.flowtail.content.BlockShapes.Template;
import dev.flowtail.content.ProjectionSpec.Face;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BlockShapesTest {
    /** A tiny world for the placement rules: stairs, sturdy faces, soil and power by offset. */
    private static final class World implements Surroundings {
        final Map<String, StairAt> stairs = new HashMap<>();
        final List<String> sturdy = new ArrayList<>(); // "dx,dy,dz,FACE"
        final List<String> present = new ArrayList<>();
        final List<String> soil = new ArrayList<>();
        boolean powered;
        World stair(int dx, int dz, Dir facing, String half) { stairs.put(dx + "," + dz, new StairAt(facing, half)); return this; }
        World sturdyAt(int dx, int dy, int dz, Face face) { sturdy.add(dx + "," + dy + "," + dz + "," + face); return this; }
        @Override public StairAt stairAt(int dx, int dy, int dz) { return dy == 0 ? stairs.get(dx + "," + dz) : null; }
        @Override public boolean sturdy(int dx, int dy, int dz, Face face, Support support) { return sturdy.contains(dx + "," + dy + "," + dz + "," + face); }
        @Override public boolean present(int dx, int dy, int dz) { return present.contains(dx + "," + dy + "," + dz); }
        @Override public boolean soil(int dx, int dy, int dz) { return soil.contains(dx + "," + dy + "," + dz); }
        @Override public boolean powered() { return powered; }
    }

    private static PlaceContext click(Face face, double relY, Dir looking) { return new PlaceContext(face, relY, looking, false); }

    // ------------------------------------------------------------------------------------------- the compiler contract

    @Test
    void variantNamesEqualTheCompilersGoldenFile() throws Exception {
        Path golden = Path.of("..", "compiler", "tests", "golden", "shape-variants.json");
        String text = Files.readString(golden);
        Matcher entries = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\[([^\\]]*)]").matcher(text);
        Map<String, List<String>> expected = new TreeMap<>();
        while (entries.find()) {
            List<String> names = new ArrayList<>();
            Matcher name = Pattern.compile("\"([a-z0-9_]+)\"").matcher(entries.group(2));
            while (name.find()) names.add(name.group(1));
            expected.put(entries.group(1), names);
        }
        assertEquals(8, expected.size(), "slab stairs trapdoor button pressure_plate carpet plant shelf");
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            Template template = Template.valueOf(entry.getKey().toUpperCase());
            assertEquals(entry.getValue(), BlockShapes.variantNames(template), "variants of " + entry.getKey()
                    + " differ from compiler/ftc/shapes.py; change both, then run test_shapes.py --update-golden");
        }
    }

    @Test
    void everyStateHasAVariantAndEveryVariantComesFromAState() {
        for (Template template : Template.values()) {
            if (!template.shaped()) continue;
            for (Map<String, String> state : BlockShapes.allStates(template)) {
                assertTrue(BlockShapes.valid(template, state), template + " " + state);
                assertTrue(BlockShapes.variantNames(template).contains(BlockShapes.variant(template, state)));
            }
        }
        assertEquals(40, BlockShapes.allStates(Template.STAIRS).size());
        assertEquals(40, BlockShapes.variantNames(Template.STAIRS).size());
        assertEquals(24, BlockShapes.allStates(Template.BUTTON).size());
        assertEquals(24, BlockShapes.variantNames(Template.BUTTON).size(), "face x facing x pressed");
        assertEquals(16, BlockShapes.allStates(Template.TRAPDOOR).size());
        assertEquals(6, BlockShapes.variantNames(Template.TRAPDOOR).size(), "open ignores half, closed ignores facing");
        assertEquals(32, BlockShapes.allStates(Template.SHELF).size(), "facing x side chain x powered");
        assertEquals(32, BlockShapes.variantNames(Template.SHELF).size());
    }

    @Test
    void serverOwnedGeometryUsesEachSavedVariantAndStaysInsideItsBlock() {
        assertEquals(List.of(new Box(0, 0, 0, 1, 0.5, 1)),
                BlockShapes.geometry(Template.SLAB, Map.of("type", "bottom")).collision());
        assertEquals(List.of(new Box(0, 0.5, 0, 1, 1, 1)),
                BlockShapes.geometry(Template.SLAB, Map.of("type", "top")).outline());
        assertEquals(List.of(new Box(0, 0, 0, 1, 0.5, 1), new Box(0.5, 0.5, 0, 1, 1, 0.5),
                        new Box(0.5, 0.5, 0.5, 1, 1, 1)),
                BlockShapes.geometry(Template.STAIRS, Map.of("facing", "east", "half", "bottom", "shape", "straight")).collision());
        assertEquals(List.of(new Box(0, 0, 13.0 / 16, 1, 1, 1)),
                BlockShapes.geometry(Template.TRAPDOOR, Map.of("facing", "north", "half", "bottom", "open", "true")).outline());
        assertEquals(List.of(new Box(5.0 / 16, 0, 6.0 / 16, 11.0 / 16, 1.0 / 16, 10.0 / 16)),
                BlockShapes.geometry(Template.BUTTON, Map.of("face", "floor", "facing", "north", "powered", "true")).collision());
        assertEquals(List.of(new Box(0, 0, 0, 1, 1.0 / 16, 1)),
                BlockShapes.geometry(Template.CARPET, Map.of()).collision());
        assertTrue(BlockShapes.geometry(Template.PLANT, Map.of()).collision().isEmpty());
        assertFalse(BlockShapes.geometry(Template.PLANT, Map.of()).outline().isEmpty());

        for (Template template : Template.values()) {
            for (Map<String, String> state : BlockShapes.allStates(template)) {
                Geometry geometry = BlockShapes.geometry(template, state);
                for (Box box : geometry.collision()) assertUnitBox(box, template + " collision " + state);
                for (Box box : geometry.outline()) assertUnitBox(box, template + " outline " + state);
            }
        }
    }

    private static void assertUnitBox(Box box, String message) {
        assertTrue(box.minX() >= 0 && box.minY() >= 0 && box.minZ() >= 0, message);
        assertTrue(box.maxX() <= 1 && box.maxY() <= 1 && box.maxZ() <= 1, message);
        assertTrue(box.minX() < box.maxX() && box.minY() < box.maxY() && box.minZ() < box.maxZ(), message);
    }

    @Test
    void variantNamesForKnownStates() {
        assertEquals("top", BlockShapes.variant(Template.SLAB, Map.of("type", "top")));
        assertEquals("top_inner_left_west", BlockShapes.variant(Template.STAIRS, Map.of("half", "top", "shape", "inner_left", "facing", "west")));
        assertEquals("open_east", BlockShapes.variant(Template.TRAPDOOR, Map.of("half", "top", "facing", "east", "open", "true")));
        assertEquals("top_closed", BlockShapes.variant(Template.TRAPDOOR, Map.of("half", "top", "facing", "east", "open", "false")));
        assertEquals("wall_south_on", BlockShapes.variant(Template.BUTTON, Map.of("face", "wall", "facing", "south", "powered", "true")));
        assertEquals("down", BlockShapes.variant(Template.PRESSURE_PLATE, Map.of("powered", "true")));
        assertEquals("east_center_on", BlockShapes.variant(Template.SHELF,
                Map.of("facing", "east", "side_chain", "center", "powered", "true")));
        assertEquals("carpet", BlockShapes.variant(Template.CARPET, Map.of()));
        assertEquals(null, BlockShapes.variant(Template.CUBE, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> BlockShapes.variant(Template.SLAB, Map.of("type", "middle")));
    }

    // -------------------------------------------------------------------------------------------------- carrier data

    @Test
    void carrierStringAndStateRoundTripForEveryState() {
        for (Template template : Template.values()) {
            if (!template.shaped() || template == Template.PLANT || template == Template.CARPET) continue;
            for (Map<String, String> state : BlockShapes.allStates(template)) {
                String carrier = BlockShapes.carrierString(template, "oak_whatever", state);
                assertTrue(carrier.startsWith("minecraft:oak_whatever["), carrier);
                // Paper's getAsString adds waterlogged and powered and sorts the properties: reading must ignore the extras.
                String live = carrier.replace("]", ",waterlogged=false]");
                assertEquals(state, BlockShapes.stateFromCarrier(template, live).orElseThrow(), live);
            }
        }
        assertEquals("minecraft:white_carpet", BlockShapes.carrierString(Template.CARPET, "white_carpet", Map.of()));
        assertEquals("minecraft:light[level=0]", BlockShapes.carrierString(Template.PLANT, "light", Map.of()));
        assertEquals("minecraft:oak_shelf[facing=north,side_chain=unconnected,powered=false]",
                BlockShapes.carrierString(Template.SHELF, "oak_shelf", BlockShapes.defaultState(Template.SHELF)));
        assertEquals("minecraft:barrier", BlockShapes.carrierString(Template.CUBE, "barrier", Map.of()));
    }

    @Test
    void aBlockThatIsNotACarrierOfTheTemplateIsRejected() {
        assertTrue(BlockShapes.stateFromCarrier(Template.STAIRS, "minecraft:barrier[waterlogged=false]").isEmpty());
        assertTrue(BlockShapes.stateFromCarrier(Template.SLAB, "minecraft:stone").isEmpty());
        assertTrue(BlockShapes.stateFromCarrier(Template.SLAB, "minecraft:stone_slab[type=sideways]").isEmpty());
        assertTrue(BlockShapes.stateFromCarrier(Template.TRAPDOOR, "minecraft:oak_trapdoor[half=top,open=true]").isEmpty(), "facing missing");
        assertEquals(Map.of("type", "double"), BlockShapes.stateFromCarrier(Template.SLAB, "minecraft:stone_slab[type=double,waterlogged=true]").orElseThrow());
    }

    @Test
    void stateTextRoundTrip() {
        Map<String, String> state = BlockShapes.defaultState(Template.STAIRS);
        assertEquals("facing=north,half=bottom,shape=straight", BlockShapes.format(state));
        assertEquals(state, BlockShapes.parse(BlockShapes.format(state)));
        assertEquals(Map.of(), BlockShapes.parse(""));
        assertEquals("", BlockShapes.format(Map.of()));
    }

    // ----------------------------------------------------------------------------------------------- directions, slabs

    @Test
    void yawToDirectionUsesMinecraftYaw() {
        assertEquals(Dir.SOUTH, BlockShapes.dirFromYaw(0));
        assertEquals(Dir.WEST, BlockShapes.dirFromYaw(90));
        assertEquals(Dir.NORTH, BlockShapes.dirFromYaw(180));
        assertEquals(Dir.NORTH, BlockShapes.dirFromYaw(-180));
        assertEquals(Dir.EAST, BlockShapes.dirFromYaw(-90));
        assertEquals(Dir.EAST, BlockShapes.dirFromYaw(270));
        assertEquals(Dir.SOUTH, BlockShapes.dirFromYaw(44));
        assertEquals(Dir.WEST, BlockShapes.dirFromYaw(46));
        assertEquals(Dir.SOUTH, BlockShapes.dirFromYaw(-44));
        assertEquals(Dir.EAST, BlockShapes.dirFromYaw(-46));
        assertEquals(Dir.SOUTH, BlockShapes.dirFromYaw(360));
    }

    @Test
    void directionTurns() {
        assertEquals(Dir.EAST, Dir.NORTH.cw());
        assertEquals(Dir.WEST, Dir.NORTH.ccw());
        assertEquals(Dir.SOUTH, Dir.NORTH.opposite());
        assertEquals(Dir.NORTH, Dir.WEST.cw());
        assertEquals(Dir.EAST, Dir.SOUTH.ccw(), "vanilla: counter-clockwise of south is east");
    }

    @Test
    void slabHalfFollowsTheClickLikeVanilla() {
        World world = new World();
        assertEquals("bottom", BlockShapes.place(Template.SLAB, click(Face.UP, 0.0, Dir.NORTH), world).orElseThrow().get("type"));
        assertEquals("top", BlockShapes.place(Template.SLAB, click(Face.DOWN, 1.0, Dir.NORTH), world).orElseThrow().get("type"));
        assertEquals("bottom", BlockShapes.place(Template.SLAB, click(Face.NORTH, 0.3, Dir.NORTH), world).orElseThrow().get("type"));
        assertEquals("bottom", BlockShapes.place(Template.SLAB, click(Face.NORTH, 0.5, Dir.NORTH), world).orElseThrow().get("type"));
        assertEquals("top", BlockShapes.place(Template.SLAB, click(Face.NORTH, 0.51, Dir.NORTH), world).orElseThrow().get("type"));
    }

    @Test
    void slabsMergeLikeVanilla() {
        assertFalse(BlockShapes.slabMerges("double", Face.UP, 0.5, true));
        assertTrue(BlockShapes.slabMerges("bottom", Face.UP, 0.5, true), "top face of a bottom slab");
        assertFalse(BlockShapes.slabMerges("bottom", Face.DOWN, 0.5, true));
        assertTrue(BlockShapes.slabMerges("bottom", Face.NORTH, 0.8, true), "upper half of a side of a bottom slab");
        assertFalse(BlockShapes.slabMerges("bottom", Face.NORTH, 0.2, true), "lower half of a side: a new slab goes next to it");
        assertTrue(BlockShapes.slabMerges("top", Face.DOWN, 0.5, true));
        assertTrue(BlockShapes.slabMerges("top", Face.EAST, 0.2, true));
        assertFalse(BlockShapes.slabMerges("top", Face.EAST, 0.8, true));
        assertTrue(BlockShapes.slabMerges("top", Face.EAST, 0.8, false), "not replacing the clicked block: always merges");
    }

    // ------------------------------------------------------------------------------------------------------ stairs

    @Test
    void stairsFaceTheWayThePlayerLooksAndTakeTheHalfFromTheClick() {
        Map<String, String> state = BlockShapes.place(Template.STAIRS, click(Face.UP, 0, Dir.EAST), new World()).orElseThrow();
        assertEquals(Map.of("facing", "east", "half", "bottom", "shape", "straight"), state);
        state = BlockShapes.place(Template.STAIRS, click(Face.DOWN, 1, Dir.WEST), new World()).orElseThrow();
        assertEquals(Map.of("facing", "west", "half", "top", "shape", "straight"), state);
        state = BlockShapes.place(Template.STAIRS, click(Face.SOUTH, 0.9, Dir.NORTH), new World()).orElseThrow();
        assertEquals("top", state.get("half"));
    }

    @Test
    void stairCornersFollowTheVanillaRule() {
        // our stair faces north; a stair in FRONT (north) facing west is our counter-clockwise side -> outer_left, east -> outer_right
        assertEquals("outer_left", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, -1, Dir.WEST, "bottom")));
        assertEquals("outer_right", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, -1, Dir.EAST, "bottom")));
        // a stair BEHIND (south) facing west -> inner_left, east -> inner_right
        assertEquals("inner_left", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, 1, Dir.WEST, "bottom")));
        assertEquals("inner_right", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, 1, Dir.EAST, "bottom")));
        // the same axis, another half, or no stair: straight
        assertEquals("straight", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, -1, Dir.NORTH, "bottom")));
        assertEquals("straight", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World().stair(0, -1, Dir.WEST, "top")));
        assertEquals("straight", BlockShapes.stairsShape(Dir.NORTH, "bottom", new World()));
    }

    @Test
    void stairCornersAreMirrorImages() {
        // mirroring east-west swaps left and right and nothing else
        for (String half : List.of("bottom", "top")) {
            for (Dir facing : Dir.values()) {
                for (int where = 0; where < 2; where++) {
                    for (Dir neighbour : Dir.values()) {
                        Dir at = where == 0 ? facing : facing.opposite();
                        World world = new World().stair(at.dx, at.dz, neighbour, half);
                        String shape = BlockShapes.stairsShape(facing, half, world);
                        if (shape.equals("straight")) continue;
                        // reflect across the facing axis: the neighbour's facing turns the other way round
                        Dir mirrored = neighbour.ordinal() % 2 == facing.ordinal() % 2 ? neighbour : neighbour.opposite();
                        String other = BlockShapes.stairsShape(facing, half, new World().stair(at.dx, at.dz, mirrored, half));
                        assertEquals(shape.replace("left", "L").replace("right", "left").replace("L", "right"), other, facing + " " + neighbour);
                    }
                }
            }
        }
    }

    @Test
    void aStairBesideTwoStairsOfTheSameKindDoesNotTakeAThirdShape() {
        // vanilla canTakeShape: the block on the far side must not already be a same-facing stair of the same half
        World world = new World().stair(0, -1, Dir.WEST, "bottom").stair(-1, 0, Dir.NORTH, "bottom");
        // the stair at (0,-1) facing west gives outer_left, but the block on its opposite() side... here east (1,0) is empty so it holds
        assertEquals("outer_left", BlockShapes.stairsShape(Dir.NORTH, "bottom", world));
        World blocked = new World().stair(0, -1, Dir.WEST, "bottom").stair(1, 0, Dir.NORTH, "bottom");
        assertEquals("straight", BlockShapes.stairsShape(Dir.NORTH, "bottom", blocked), "(1,0) is a same-facing same-half stair");
    }

    // --------------------------------------------------------------------------------------- trapdoor, button, plate

    @Test
    void trapdoorPlacementFollowsVanilla() {
        Map<String, String> onWall = BlockShapes.place(Template.TRAPDOOR, click(Face.EAST, 0.8, Dir.NORTH), new World()).orElseThrow();
        assertEquals(Map.of("facing", "east", "half", "top", "open", "false"), onWall);
        Map<String, String> onFloor = BlockShapes.place(Template.TRAPDOOR, click(Face.UP, 0, Dir.NORTH), new World()).orElseThrow();
        assertEquals(Map.of("facing", "south", "half", "bottom", "open", "false"), onFloor, "facing is opposite the player's look");
        Map<String, String> onCeiling = BlockShapes.place(Template.TRAPDOOR, click(Face.DOWN, 1, Dir.WEST), new World()).orElseThrow();
        assertEquals(Map.of("facing", "east", "half", "top", "open", "false"), onCeiling);
        Map<String, String> replacing = BlockShapes.place(Template.TRAPDOOR, new PlaceContext(Face.EAST, 0.8, Dir.NORTH, true), new World()).orElseThrow();
        assertEquals("south", replacing.get("facing"), "replacing the clicked block uses the player's look even on a side face");
        World powered = new World();
        powered.powered = true;
        assertEquals("true", BlockShapes.place(Template.TRAPDOOR, click(Face.UP, 0, Dir.NORTH), powered).orElseThrow().get("open"));
    }

    @Test
    void buttonAttachesToTheClickedFaceAndNeedsSupport() {
        World floor = new World().sturdyAt(0, -1, 0, Face.UP);
        assertEquals(Map.of("face", "floor", "facing", "west", "powered", "false"),
                BlockShapes.place(Template.BUTTON, click(Face.UP, 0, Dir.WEST), floor).orElseThrow());
        World ceiling = new World().sturdyAt(0, 1, 0, Face.DOWN);
        assertEquals("ceiling", BlockShapes.place(Template.BUTTON, click(Face.DOWN, 1, Dir.WEST), ceiling).orElseThrow().get("face"));
        // clicking the east face of a block puts the button on its east side, facing east, attached to the block on its WEST
        World wall = new World().sturdyAt(-1, 0, 0, Face.EAST);
        assertEquals(Map.of("face", "wall", "facing", "east", "powered", "false"),
                BlockShapes.place(Template.BUTTON, click(Face.EAST, 0.5, Dir.NORTH), wall).orElseThrow());
        assertTrue(BlockShapes.place(Template.BUTTON, click(Face.EAST, 0.5, Dir.NORTH), new World()).isEmpty(), "no support: not placed");
        assertTrue(BlockShapes.place(Template.BUTTON, click(Face.UP, 0, Dir.NORTH), new World()).isEmpty());
    }

    @Test
    void plateCarpetAndPlantNeedTheirSupport() {
        assertTrue(BlockShapes.place(Template.PRESSURE_PLATE, click(Face.UP, 0, Dir.NORTH), new World()).isEmpty());
        assertTrue(BlockShapes.place(Template.PRESSURE_PLATE, click(Face.UP, 0, Dir.NORTH), new World().sturdyAt(0, -1, 0, Face.UP)).isPresent());
        World none = new World();
        assertTrue(BlockShapes.place(Template.CARPET, click(Face.UP, 0, Dir.NORTH), none).isEmpty());
        none.present.add("0,-1,0");
        assertTrue(BlockShapes.place(Template.CARPET, click(Face.UP, 0, Dir.NORTH), none).isPresent());
        World soil = new World();
        assertTrue(BlockShapes.place(Template.PLANT, click(Face.UP, 0, Dir.NORTH), soil).isEmpty());
        soil.soil.add("0,-1,0");
        assertTrue(BlockShapes.place(Template.PLANT, click(Face.UP, 0, Dir.NORTH), soil).isPresent());
        World noWall = new World();
        assertTrue(BlockShapes.place(Template.SHELF, click(Face.NORTH, 0.5, Dir.SOUTH), noWall).isEmpty());
        noWall.sturdyAt(0, 0, 1, Face.NORTH);
        assertTrue(BlockShapes.place(Template.SHELF, click(Face.NORTH, 0.5, Dir.SOUTH), noWall).isPresent());
        assertTrue(BlockShapes.survives(Template.SLAB, Map.of("type", "top"), new World()), "slabs float like vanilla slabs");
        assertTrue(BlockShapes.survives(Template.STAIRS, BlockShapes.defaultState(Template.STAIRS), new World()));
    }

    @Test
    void templateFlags() {
        assertEquals(Template.SHELF, Template.of("shelf"));
        assertEquals(Template.CUBE, Template.of(null));
        assertEquals(Template.PRESSURE_PLATE, Template.of("pressure_plate"));
        assertTrue(Template.TRAPDOOR.interactive() && Template.BUTTON.interactive() && Template.PRESSURE_PLATE.interactive() && Template.SHELF.interactive());
        assertFalse(Template.SLAB.interactive());
        assertFalse(Template.STAIRS.followsNeighbours());
        for (Template template : Template.values()) assertTrue(template.hasInteractionBox());
        for (Template template : Template.values()) assertFalse(template.waterloggable());
        assertFalse(Template.BUTTON.waterloggable());
    }
}
