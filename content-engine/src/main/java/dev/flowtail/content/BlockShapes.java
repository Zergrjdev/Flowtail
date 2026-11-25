package dev.flowtail.content;

import dev.flowtail.content.ProjectionSpec.Face;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Shape templates (P3-01, ADR-031): slab, stairs, trapdoor, button, pressure plate, carpet, plant, shelf. Pure logic with no
 * Bukkit types, so it is unit-tested without a server.
 *
 * <p>A shaped block uses FlowTail's barrier hitbox and an item display (drawn from the compiler's per-variant model).
 * This class decides, from a block's saved FlowTail state, which variant model to show and, at placement, which state
 * a click produces (vanilla-style placement rules, ported). Vanilla blocks are never used as carriers. The variant
 * names are a contract with {@code compiler/ftc/shapes.py};
 * {@code BlockShapesTest} checks them against {@code compiler/tests/golden/shape-variants.json}.
 *
 * <p>State maps hold only what the DISPLAY needs (slab type; stairs facing/half/shape; trapdoor facing/half/open;
 * button face/facing/powered; plate powered). State is saved beside the content id in FlowTail's chunk record.
 */
final class BlockShapes {
    private BlockShapes() {}

    /** Unit-cube box used for FlowTail's server-owned collision and selection shapes. */
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    record Geometry(List<Box> collision, List<Box> outline) {}

    /** Shape geometry in block-local coordinates; the renderer's slight model expansion is deliberately excluded. */
    static Geometry geometry(Template template, Map<String, String> rawState) {
        Map<String, String> state = valid(template, rawState) ? rawState : defaultState(template);
        List<Box> boxes = switch (template) {
            case CUBE -> List.of(box(0, 0, 0, 16, 16, 16));
            case SLAB -> switch (state.get("type")) {
                case "top" -> List.of(box(0, 8, 0, 16, 16, 16));
                case "double" -> List.of(box(0, 0, 0, 16, 16, 16));
                default -> List.of(box(0, 0, 0, 16, 8, 16));
            };
            case STAIRS -> stairBoxes(state);
            case TRAPDOOR -> trapdoorBoxes(state);
            case BUTTON -> buttonBoxes(state);
            case PRESSURE_PLATE -> List.of(state.get("powered").equals("true")
                    ? box(1, 0, 1, 15, 0.5, 15) : box(1, 0, 1, 15, 1, 15));
            case CARPET -> List.of(box(0, 0, 0, 16, 1, 16));
            case PLANT -> List.of();
            case SHELF -> shelfBoxes(state);
        };
        List<Box> collision = template == Template.PLANT ? List.of() : List.copyOf(boxes);
        List<Box> outline = template == Template.PLANT
                ? List.of(box(4, 0, 4, 12, 15, 12)) : List.copyOf(boxes);
        return new Geometry(collision, outline);
    }

    private static Box box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new Box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    private static List<Box> stairBoxes(Map<String, String> state) {
        String half = state.get("half"), shape = state.get("shape"), facing = state.get("facing");
        int mask = switch (shape) { case "inner_left" -> 7; case "inner_right" -> 11; case "outer_left" -> 1; case "outer_right" -> 2; default -> 3; };
        double baseMinY = half.equals("bottom") ? 0 : 8, baseMaxY = half.equals("bottom") ? 8 : 16;
        double stepMinY = half.equals("bottom") ? 8 : 0, stepMaxY = half.equals("bottom") ? 16 : 8;
        List<Box> boxes = new ArrayList<>();
        boxes.add(box(0, baseMinY, 0, 16, baseMaxY, 16));
        int turns = List.of("north", "east", "south", "west").indexOf(facing);
        int bit = 1;
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++, bit <<= 1) {
            if ((mask & bit) == 0) continue;
            Box octant = box(x * 8, stepMinY, z * 8, (x + 1) * 8, stepMaxY, (z + 1) * 8);
            boxes.add(rotate(octant, turns));
        }
        return List.copyOf(boxes);
    }

    private static List<Box> trapdoorBoxes(Map<String, String> state) {
        if (!state.get("open").equals("true")) {
            return List.of(state.get("half").equals("top") ? box(0, 13, 0, 16, 16, 16) : box(0, 0, 0, 16, 3, 16));
        }
        return List.of(rotate(box(0, 0, 13, 16, 16, 16), List.of("north", "east", "south", "west").indexOf(state.get("facing"))));
    }

    private static List<Box> buttonBoxes(Map<String, String> state) {
        double depth = state.get("powered").equals("true") ? 1 : 2;
        Box button = switch (state.get("face")) {
            case "ceiling" -> box(5, 16 - depth, 6, 11, 16, 10);
            case "wall" -> box(5, 6, 16 - depth, 11, 10, 16);
            default -> box(5, 0, 6, 11, depth, 10);
        };
        return List.of(rotate(button, List.of("north", "east", "south", "west").indexOf(state.get("facing"))));
    }

    private static List<Box> shelfBoxes(Map<String, String> state) {
        List<Box> base = List.of(box(0, 0, 13, 16, 16, 16), box(0, 0, 11, 16, 4, 13),
                box(0, 12, 11, 16, 16, 13), box(0, 4, 12.9, 16, 12, 13.1));
        int turns = List.of("north", "east", "south", "west").indexOf(state.get("facing"));
        return base.stream().map(item -> rotate(item, turns)).toList();
    }

    /** Same clockwise quarter-turn convention as the generated, UV-locked item-display models. */
    private static Box rotate(Box box, int turns) {
        double x1 = box.minX() * 16, y1 = box.minY() * 16, z1 = box.minZ() * 16;
        double x2 = box.maxX() * 16, y2 = box.maxY() * 16, z2 = box.maxZ() * 16;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            double nextX1 = 16 - z2, nextX2 = 16 - z1, nextZ1 = x1, nextZ2 = x2;
            x1 = nextX1; x2 = nextX2; z1 = nextZ1; z2 = nextZ2;
        }
        return box(x1, y1, z1, x2, y2, z2);
    }

    enum Template {
        CUBE, SLAB, STAIRS, TRAPDOOR, BUTTON, PRESSURE_PLATE, CARPET, PLANT, SHELF;

        /** Reads the schema's template name; anything not built here (custom_model, typos) is a cube. */
        static Template of(String name) {
            if (name == null) return CUBE;
            try { return valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException error) { return CUBE; }
        }

        boolean shaped() { return this != CUBE; }

        /** Templates with a custom right-click or step action handled by FlowTail. */
        boolean interactive() { return this == TRAPDOOR || this == BUTTON || this == PRESSURE_PLATE || this == SHELF; }

        /** State and neighbor behavior are owned by FlowTail, not a vanilla carrier. */
        boolean followsNeighbours() { return false; }

        /** No vanilla block state is used to carry waterlogging. */
        boolean waterloggable() { return false; }

        /** Every FlowTail block uses its own interaction entity. */
        boolean hasInteractionBox() { return true; }
    }

    enum Dir {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);
        final int dx, dz;
        Dir(int dx, int dz) { this.dx = dx; this.dz = dz; }
        Dir cw() { return values()[(ordinal() + 1) % 4]; }
        Dir ccw() { return values()[(ordinal() + 3) % 4]; }
        Dir opposite() { return values()[(ordinal() + 2) % 4]; }
        String key() { return name().toLowerCase(Locale.ROOT); }
        static Dir of(String key) { return valueOf(key.toUpperCase(Locale.ROOT)); }
        Face face() { return switch (this) { case NORTH -> Face.NORTH; case EAST -> Face.EAST; case SOUTH -> Face.SOUTH; case WEST -> Face.WEST; }; }
    }

    /** The horizontal direction a player with this Minecraft yaw looks toward (yaw 0 = south, 90 = west, 180 = north, -90 = east). */
    static Dir dirFromYaw(float yaw) {
        int quarter = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
        return switch (quarter) { case 0 -> Dir.SOUTH; case 1 -> Dir.WEST; case 2 -> Dir.NORTH; default -> Dir.EAST; };
    }

    static Dir dirOf(Face face) {
        return switch (face) { case NORTH -> Dir.NORTH; case SOUTH -> Dir.SOUTH; case EAST -> Dir.EAST; case WEST -> Dir.WEST; default -> null; };
    }

    // ------------------------------------------------------------------------------------------------ state text

    /** {@code k=v,k=v} sorted by key; empty for a stateless template. */
    static String format(Map<String, String> state) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(state).entrySet()) {
            if (out.length() > 0) out.append(',');
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    static Map<String, String> parse(String text) {
        TreeMap<String, String> out = new TreeMap<>();
        if (text == null || text.isBlank()) return out;
        for (String part : text.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) out.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
        }
        return out;
    }

    private static Map<String, String> of(String... pairs) {
        TreeMap<String, String> out = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }

    static Map<String, String> defaultState(Template template) {
        return switch (template) {
            case SLAB -> of("type", "bottom");
            case STAIRS -> of("facing", "north", "half", "bottom", "shape", "straight");
            case TRAPDOOR -> of("facing", "north", "half", "bottom", "open", "false");
            case BUTTON -> of("face", "floor", "facing", "north", "powered", "false");
            case PRESSURE_PLATE -> of("powered", "false");
            case SHELF -> of("facing", "north", "side_chain", "unconnected", "powered", "false");
            default -> of();
        };
    }

    /** The state keys the display depends on, per template. */
    static List<String> keys(Template template) {
        return new ArrayList<>(defaultState(template).keySet());
    }

    /** True when the state has every key of the template with a legal value (so a variant exists for it). */
    static boolean valid(Template template, Map<String, String> state) {
        return variantOrNull(template, state) != null;
    }

    // --------------------------------------------------------------------------------------------------- variants

    private static final List<String> HALVES = List.of("bottom", "top");
    private static final List<String> STAIR_SHAPES = List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");
    private static final List<String> FACES = List.of("floor", "wall", "ceiling");

    /** The model variant name for a state; must exist in the pack. {@code null} for a cube (it has one model). */
    static String variant(Template template, Map<String, String> state) {
        String found = variantOrNull(template, state);
        if (found == null && template.shaped()) throw new IllegalArgumentException("no variant for " + template + " " + format(state));
        return found;
    }

    private static String variantOrNull(Template template, Map<String, String> s) {
        switch (template) {
            case SLAB: {
                String type = s.get("type");
                return List.of("bottom", "top", "double").contains(type) ? type : null;
            }
            case STAIRS: {
                String half = s.get("half"), shape = s.get("shape"), facing = s.get("facing");
                return HALVES.contains(half) && STAIR_SHAPES.contains(shape) && isHorizontal(facing) ? half + "_" + shape + "_" + facing : null;
            }
            case TRAPDOOR: {
                String half = s.get("half"), facing = s.get("facing"), open = s.get("open");
                if (!HALVES.contains(half) || !isHorizontal(facing) || !isBool(open)) return null;
                return open.equals("true") ? "open_" + facing : half + "_closed";
            }
            case BUTTON: {
                String face = s.get("face"), facing = s.get("facing"), powered = s.get("powered");
                return FACES.contains(face) && isHorizontal(facing) && isBool(powered)
                        ? face + "_" + facing + "_" + (powered.equals("true") ? "on" : "off") : null;
            }
            case PRESSURE_PLATE: {
                String powered = s.get("powered");
                return isBool(powered) ? (powered.equals("true") ? "down" : "up") : null;
            }
            case SHELF: {
                String facing = s.get("facing"), chain = s.get("side_chain"), powered = s.get("powered");
                return isHorizontal(facing) && List.of("unconnected", "left", "center", "right").contains(chain)
                        && isBool(powered) ? facing + "_" + chain + "_" + (powered.equals("true") ? "on" : "off") : null;
            }
            case CARPET: return "carpet";
            case PLANT: return "plant";
            default: return null;
        }
    }

    private static boolean isHorizontal(String v) { return v != null && List.of("north", "east", "south", "west").contains(v); }
    private static boolean isBool(String v) { return "true".equals(v) || "false".equals(v); }

    /** Every state of a template (stateless templates have exactly one empty state). */
    static List<Map<String, String>> allStates(Template template) {
        List<Map<String, String>> out = new ArrayList<>();
        List<String> dirs = List.of("north", "east", "south", "west");
        switch (template) {
            case SLAB -> { for (String t : List.of("bottom", "top", "double")) out.add(of("type", t)); }
            case STAIRS -> { for (String h : HALVES) for (String sh : STAIR_SHAPES) for (String f : dirs) out.add(of("facing", f, "half", h, "shape", sh)); }
            case TRAPDOOR -> { for (String h : HALVES) for (String f : dirs) for (String o : List.of("false", "true")) out.add(of("facing", f, "half", h, "open", o)); }
            case BUTTON -> { for (String fa : FACES) for (String f : dirs) for (String p : List.of("false", "true")) out.add(of("face", fa, "facing", f, "powered", p)); }
            case PRESSURE_PLATE -> { for (String p : List.of("false", "true")) out.add(of("powered", p)); }
            case SHELF -> {
                for (String facing : dirs) for (String chain : List.of("unconnected", "left", "center", "right"))
                    for (String powered : List.of("false", "true")) out.add(of("facing", facing, "side_chain", chain, "powered", powered));
            }
            default -> out.add(of());
        }
        return out;
    }

    /** Sorted distinct variant names of a template (what the pack must contain). */
    static List<String> variantNames(Template template) {
        List<String> names = new ArrayList<>();
        for (Map<String, String> state : allStates(template)) {
            String name = variantOrNull(template, state);
            if (name != null && !names.contains(name)) names.add(name);
        }
        Collections.sort(names);
        return names;
    }

    // ---------------------------------------------------------------------------------------------- carrier data

    /**
     * Legacy vanilla block-data string formatter used by migration tests. New FlowTail placements never create this
     * state; {@code material} is a historical vanilla block id without namespace.
     */
    static String carrierString(Template template, String material, Map<String, String> s) {
        String props = switch (template) {
            case SLAB -> "type=" + s.get("type");
            case STAIRS -> "facing=" + s.get("facing") + ",half=" + s.get("half") + ",shape=" + s.get("shape");
            case TRAPDOOR -> "facing=" + s.get("facing") + ",half=" + s.get("half") + ",open=" + s.get("open");
            case BUTTON -> "face=" + s.get("face") + ",facing=" + s.get("facing") + ",powered=" + s.get("powered");
            case PRESSURE_PLATE -> "powered=" + s.get("powered");
            case PLANT -> "level=0";
            case SHELF -> "facing=" + s.get("facing") + ",side_chain=" + s.get("side_chain") + ",powered=" + s.get("powered");
            default -> "";
        };
        return "minecraft:" + material + (props.isEmpty() ? "" : "[" + props + "]");
    }

    /**
     * Reads the display state back from a carrier's block-data string (Paper's {@code BlockData#getAsString()}, for
     * example {@code minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]}). Empty when a
     * needed property is missing, which means the block is not a carrier of this template.
     */
    static Optional<Map<String, String>> stateFromCarrier(Template template, String blockData) {
        if (!template.shaped()) return Optional.of(of());
        int open = blockData.indexOf('['), close = blockData.lastIndexOf(']');
        Map<String, String> all = open < 0 || close < open ? of() : parse(blockData.substring(open + 1, close));
        TreeMap<String, String> state = new TreeMap<>();
        for (String key : keys(template)) {
            String value = all.get(key);
            if (value == null) return Optional.empty();
            state.put(key, value);
        }
        return variantOrNull(template, state) == null ? Optional.empty() : Optional.of(state);
    }

    // ------------------------------------------------------------------------------------------------- placement

    /** Which kind of support a placement needs from a neighbour. */
    enum Support { FULL, RIGID }

    /** What is around the block being placed. All offsets are relative to it. Implemented over Bukkit blocks by the server. */
    interface Surroundings {
        /** The stairs at an offset (any stairs, vanilla or FlowTail), or null. */
        StairAt stairAt(int dx, int dy, int dz);
        /** True when the block at the offset has a sturdy {@code face} for the given kind of support. */
        boolean sturdy(int dx, int dy, int dz, Face face, Support support);
        /** True when the block at the offset is not air (carpet needs something under it). */
        boolean present(int dx, int dy, int dz);
        /** True when the block at the offset is plant soil (dirt-like or farmland). */
        boolean soil(int dx, int dy, int dz);
        /** True when the block receives a redstone signal (a placed trapdoor opens at once, as in vanilla). */
        boolean powered();
    }

    record StairAt(Dir facing, String half) {}

    /** The click: clicked face, height of the click inside the TARGET block (0..1), the player's horizontal look, and whether the clicked block is itself the target. */
    record PlaceContext(Face face, double relY, Dir playerFacing, boolean replacingClicked) {}

    private static String halfFor(PlaceContext ctx) {
        return ctx.face() != Face.DOWN && (ctx.face() == Face.UP || ctx.relY() <= 0.5) ? "bottom" : "top";
    }

    /**
     * Vanilla slab merging ({@code SlabBlock#canBeReplaced}): a slab of the same block, not yet double, takes another
     * slab when the click is on its open face, or on the matching half of a side face. {@code relYClicked} is the
     * click height inside the existing slab's block.
     */
    static boolean slabMerges(String existingType, Face face, double relYClicked, boolean replacingClicked) {
        if (existingType.equals("double")) return false;
        if (!replacingClicked) return true;
        boolean clickedTop = relYClicked > 0.5;
        boolean horizontal = face != Face.UP && face != Face.DOWN;
        return existingType.equals("bottom") ? face == Face.UP || (clickedTop && horizontal)
                : face == Face.DOWN || (!clickedTop && horizontal);
    }

    /** The state a click produces, or empty when the block could not stay there (no support). */
    static Optional<Map<String, String>> place(Template template, PlaceContext ctx, Surroundings around) {
        switch (template) {
            case SLAB:
                return Optional.of(of("type", halfFor(ctx)));
            case STAIRS: {
                String half = halfFor(ctx);
                Dir facing = ctx.playerFacing();
                return Optional.of(of("facing", facing.key(), "half", half, "shape", stairsShape(facing, half, around)));
            }
            case TRAPDOOR: {
                Dir side = dirOf(ctx.face());
                String facing, half;
                if (!ctx.replacingClicked() && side != null) { facing = side.key(); half = ctx.relY() > 0.5 ? "top" : "bottom"; }
                else { facing = ctx.playerFacing().opposite().key(); half = ctx.face() == Face.UP ? "bottom" : "top"; }
                return Optional.of(of("facing", facing, "half", half, "open", String.valueOf(around.powered())));
            }
            case SHELF: {
                Dir facing = dirOf(ctx.face());
                if (facing == null) facing = ctx.playerFacing().opposite();
                Map<String, String> state = of("facing", facing.key(), "side_chain", "unconnected", "powered", String.valueOf(around.powered()));
                return survives(template, state, around) ? Optional.of(state) : Optional.empty();
            }
            case BUTTON: {
                Dir side = dirOf(ctx.face());
                Map<String, String> state = side != null
                        ? of("face", "wall", "facing", side.key(), "powered", "false")
                        : of("face", ctx.face() == Face.UP ? "floor" : "ceiling", "facing", ctx.playerFacing().key(), "powered", "false");
                return survives(template, state, around) ? Optional.of(state) : Optional.empty();
            }
            case PRESSURE_PLATE:
            case CARPET:
            case PLANT: {
                Map<String, String> state = defaultState(template);
                return survives(template, state, around) ? Optional.of(state) : Optional.empty();
            }
            default:
                return Optional.of(of());
        }
    }

    /** Vanilla support rules: what each template needs to stay in place. Slabs, stairs and trapdoors need nothing. */
    static boolean survives(Template template, Map<String, String> state, Surroundings around) {
        switch (template) {
            case BUTTON: {
                String face = state.get("face");
                if (face.equals("floor")) return around.sturdy(0, -1, 0, Face.UP, Support.FULL);
                if (face.equals("ceiling")) return around.sturdy(0, 1, 0, Face.DOWN, Support.FULL);
                Dir facing = Dir.of(state.get("facing")), behind = facing.opposite();
                return around.sturdy(behind.dx, 0, behind.dz, facing.face(), Support.FULL);
            }
            case PRESSURE_PLATE: return around.sturdy(0, -1, 0, Face.UP, Support.RIGID);
            case CARPET: return around.present(0, -1, 0);
            case PLANT: return around.soil(0, -1, 0);
            case SHELF: {
                Dir facing = Dir.of(state.get("facing")), behind = facing.opposite();
                return around.sturdy(behind.dx, 0, behind.dz, facing.face(), Support.FULL);
            }
            default: return true;
        }
    }

    /** Vanilla {@code StairBlock#getStairsShape}: how a stair joins the stairs next to it. */
    static String stairsShape(Dir facing, String half, Surroundings around) {
        StairAt front = around.stairAt(facing.dx, 0, facing.dz);
        if (front != null && front.half().equals(half) && axisDiffers(front.facing(), facing)
                && canTakeShape(facing, half, front.facing().opposite(), around)) {
            return front.facing() == facing.ccw() ? "outer_left" : "outer_right";
        }
        Dir back = facing.opposite();
        StairAt behind = around.stairAt(back.dx, 0, back.dz);
        if (behind != null && behind.half().equals(half) && axisDiffers(behind.facing(), facing)
                && canTakeShape(facing, half, behind.facing(), around)) {
            return behind.facing() == facing.ccw() ? "inner_left" : "inner_right";
        }
        return "straight";
    }

    private static boolean axisDiffers(Dir a, Dir b) { return (a.ordinal() % 2) != (b.ordinal() % 2); }

    private static boolean canTakeShape(Dir facing, String half, Dir side, Surroundings around) {
        StairAt other = around.stairAt(side.dx, 0, side.dz);
        return other == null || other.facing() != facing || !other.half().equals(half);
    }
}
