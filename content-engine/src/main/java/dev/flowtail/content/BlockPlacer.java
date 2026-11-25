package dev.flowtail.content;

import com.google.gson.JsonObject;
import dev.flowtail.content.BlockShapes.Dir;
import dev.flowtail.content.BlockShapes.PlaceContext;
import dev.flowtail.content.BlockShapes.StairAt;
import dev.flowtail.content.BlockShapes.Support;
import dev.flowtail.content.BlockShapes.Template;
import dev.flowtail.content.ProjectionSpec.Face;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockSupport;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;

/**
 * Places FlowTail blocks whose block item is not a vanilla block item (the default, ADR-022).
 *
 * <p>Vanilla only places items whose base is a block item, and the default base is deliberately not the carrier, so
 * this class does what vanilla would: finds the target, refuses occupied spaces, fires a normal
 * {@link BlockPlaceEvent} (so protection plugins can veto it; {@link BlockListener#onPlace} then records the id),
 * consumes the item and plays the block's place sound. Main thread only.
 */
final class BlockPlacer {
    private final ContentRegistry registry;
    private final BlockManager blocks;
    private final Map<UUID, Integer> lastPlacedTick = new HashMap<>();

    BlockPlacer(ContentRegistry registry, BlockManager blocks) {
        this.registry = registry; this.blocks = blocks;
    }

    void forget(UUID player) { lastPlacedTick.remove(player); }

    /** Returns true when the click was handled (placed, or vetoed after trying) and the interact event should be cancelled. */
    boolean place(Player player, Block clicked, BlockFace face, String id, ItemStack held, EquipmentSlot hand, Location clickPoint) {
        GameMode mode = player.getGameMode();
        if (!InteractRules.canBuildInMode(mode == GameMode.ADVENTURE, mode == GameMode.SPECTATOR)) return false;
        int tick = Bukkit.getCurrentTick();
        Integer last = lastPlacedTick.get(player.getUniqueId());
        if (last != null && last == tick) return true; // the other hand's event of the same click

        Template template = blocks.templateOf(id);
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        boolean merging = false;
        if (template == Template.SLAB) { // two slabs of the same block make a double slab, as in vanilla
            Block mergeAt = slabToMerge(clicked, face, id, clickPoint);
            if (mergeAt != null) { target = mergeAt; merging = true; }
        }
        if (!merging && !target.isReplaceable()) return false;
        World world = target.getWorld();
        if (target.getY() < world.getMinHeight() || target.getY() >= world.getMaxHeight()) return false;
        if (obstructed(target)) return false;

        BlockState replaced = target.getState();
        if (template.shaped()) {
            Map<String, String> state;
            if (merging) {
                state = new java.util.TreeMap<>(Map.of("type", "double"));
            } else {
                double relY = clickPoint == null ? 0.5 : clickPoint.getY() - target.getY();
                PlaceContext context = new PlaceContext(toFace(face), relY, BlockShapes.dirFromYaw(player.getLocation().getYaw()),
                        target.equals(clicked));
                Optional<Map<String, String>> placed = BlockShapes.place(template, context, new WorldSurroundings(target, blocks));
                if (placed.isEmpty()) return false; // no support here: the item is not used, like a vanilla button on air
                state = placed.get();
            }
            blocks.stageState(target, id, state);
            target.setType(BlockManager.HITBOX, false);
        } else {
            target.setType(BlockManager.HITBOX, false);
        }
        ItemStack one = held.clone();
        one.setAmount(1);
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replaced, clicked, one, player, true, hand);
        Bukkit.getPluginManager().callEvent(placeEvent);
        blocks.clearStagedState(target);
        if (placeEvent.isCancelled() || !placeEvent.canBuild() || blocks.idAt(target).isEmpty()) {
            blocks.untrack(target);
            blocks.clearProjection(target);
            replaced.update(true, false); // vetoed, or no listener recorded the block: leave the world as it was
            return true;
        }

        lastPlacedTick.put(player.getUniqueId(), tick);
        if (mode != GameMode.CREATIVE) consume(player.getInventory(), hand);
        if (hand == EquipmentSlot.OFF_HAND) player.swingOffHand(); else player.swingMainHand();
        JsonObject definition = registry.blocks().get(id);
        String preset = definition != null && definition.has("sounds") && definition.get("sounds").isJsonObject()
                && definition.getAsJsonObject("sounds").has("preset")
                ? definition.getAsJsonObject("sounds").get("preset").getAsString() : null;
        Location soundAt = target.getLocation().add(0.5, 0.5, 0.5);
        SoundPresets.placeSound(preset).ifPresent(key -> world.playSound(soundAt, key, SoundCategory.BLOCKS, 1.0f, 0.8f));
        return true;
    }

    /** The block a slab item would merge into: the clicked slab, or the slab next to the clicked face, of the same block. */
    private Block slabToMerge(Block clicked, BlockFace face, String id, Location clickPoint) {
        Optional<String> here = blocks.idAt(clicked);
        if (here.isPresent() && here.get().equals(id)) {
            String type = blocks.stateAt(clicked).get("type");
            double relY = clickPoint == null ? 0.5 : clickPoint.getY() - clicked.getY();
            if (type != null && BlockShapes.slabMerges(type, toFace(face), relY, true)) return clicked;
        }
        if (clicked.isReplaceable()) return null;
        Block next = clicked.getRelative(face);
        Optional<String> there = blocks.idAt(next);
        if (there.isPresent() && there.get().equals(id)) {
            String type = blocks.stateAt(next).get("type");
            if (type != null && BlockShapes.slabMerges(type, toFace(face), 0.5, false)) return next;
        }
        return null;
    }

    /** Recalculate FlowTail stair corners when a nearby block changes; vanilla stairs remain read-only neighbors. */
    void refreshStairsAround(Block changed) {
        for (Block candidate : new Block[] {changed, changed.getRelative(BlockFace.NORTH), changed.getRelative(BlockFace.EAST),
                changed.getRelative(BlockFace.SOUTH), changed.getRelative(BlockFace.WEST)}) {
            Optional<String> found = blocks.idAt(candidate);
            if (found.isEmpty() || blocks.templateOf(found.get()) != Template.STAIRS) continue;
            Map<String, String> state = new java.util.TreeMap<>(blocks.stateAt(candidate));
            Dir facing;
            try { facing = Dir.of(state.get("facing")); } catch (RuntimeException ex) { continue; }
            String half = state.get("half");
            String shape = BlockShapes.stairsShape(facing, half, new WorldSurroundings(candidate, blocks));
            if (!shape.equals(state.get("shape"))) {
                state.put("shape", shape);
                blocks.updateState(candidate, state);
            }
        }
    }

    static Face toFace(BlockFace face) {
        return switch (face) {
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case EAST -> Face.EAST;
            case WEST -> Face.WEST;
            default -> Face.UP;
        };
    }

    static BlockFace toBlockFace(Face face) {
        return switch (face) {
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }

    /** What is around a block in the real world, for the vanilla placement rules in {@link BlockShapes}. */
    static final class WorldSurroundings implements BlockShapes.Surroundings {
        private final Block origin;
        private final BlockManager blocks;
        WorldSurroundings(Block origin, BlockManager blocks) { this.origin = origin; this.blocks = blocks; }

        @Override public StairAt stairAt(int dx, int dy, int dz) {
            Block neighbor = origin.getRelative(dx, dy, dz);
            Optional<String> id = blocks.idAt(neighbor);
            if (id.isPresent() && blocks.templateOf(id.get()) == Template.STAIRS) {
                Map<String, String> state = blocks.stateAt(neighbor);
                return new StairAt(Dir.of(state.get("facing")), state.get("half"));
            }
            if (neighbor.getBlockData() instanceof Stairs stairs) {
                Dir facing = switch (stairs.getFacing()) {
                    case NORTH -> Dir.NORTH; case EAST -> Dir.EAST; case SOUTH -> Dir.SOUTH; default -> Dir.WEST;
                };
                return new StairAt(facing, stairs.getHalf() == Stairs.Half.TOP ? "top" : "bottom");
            }
            return null;
        }

        @Override public boolean sturdy(int dx, int dy, int dz, Face face, Support support) {
            return origin.getRelative(dx, dy, dz).getBlockData().isFaceSturdy(toBlockFace(face),
                    support == Support.RIGID ? BlockSupport.RIGID : BlockSupport.FULL);
        }

        @Override public boolean present(int dx, int dy, int dz) { return !origin.getRelative(dx, dy, dz).getType().isAir(); }

        @Override public boolean soil(int dx, int dy, int dz) {
            Material type = origin.getRelative(dx, dy, dz).getType();
            return Tag.DIRT.isTagged(type) || type == Material.FARMLAND;
        }

        @Override public boolean powered() { return origin.isBlockIndirectlyPowered(); }
    }

    private static void consume(PlayerInventory inventory, EquipmentSlot hand) {
        ItemStack stack = inventory.getItem(hand);
        if (stack == null) return;
        stack.setAmount(stack.getAmount() - 1);
        inventory.setItem(hand, stack.getAmount() > 0 ? stack : null);
    }

    /** Living entities and vehicles inside the block space stop placement; items, projectiles, markers and spectators do not. */
    private static boolean obstructed(Block target) {
        BoundingBox box = BoundingBox.of(target).expand(-0.001);
        return !target.getWorld().getNearbyEntities(box, BlockPlacer::blocksPlacement).isEmpty();
    }

    private static boolean blocksPlacement(Entity entity) {
        if (entity.isDead()) return false;
        if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
        if (entity instanceof ArmorStand stand && stand.isMarker()) return false;
        return entity instanceof LivingEntity || entity instanceof Vehicle;
    }
}
