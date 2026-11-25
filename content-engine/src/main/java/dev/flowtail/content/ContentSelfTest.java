package dev.flowtail.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * {@code /content selftest}: runs the server-side half of the Phase 2 acceptance checks inside the running server and
 * prints PASS / FAIL / SKIP lines a person can paste back. It proves server behaviour only (ids, saved records,
 * entities, event wiring); it cannot prove what a client draws, so every visual check stays a manual step.
 *
 * <p>Everything runs in one tick on a few blocks near the sender (or the world spawn for the console) and is removed
 * again in a {@code finally}, so a failing check cannot leave test blocks behind. No player block is touched: only air
 * is used.
 */
final class ContentSelfTest {
    private static final String GHOST = "selftest:ghost";
    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final ItemPrototypes prototypes;
    private final BlockManager blocks;

    ContentSelfTest(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes prototypes, BlockManager blocks) {
        this.plugin = plugin; this.registry = registry; this.prototypes = prototypes; this.blocks = blocks;
    }

    SelfTestReport run(CommandSender sender) {
        SelfTestReport report = new SelfTestReport();
        items(report, sender instanceof Player player ? player : null);
        blocks(report, sender);
        return report;
    }

    // ---------------------------------------------------------------- items

    private void items(SelfTestReport report, Player player) {
        if (prototypes.ids().isEmpty()) { report.skip("items", "no items are registered"); return; }
        for (String id : prototypes.ids()) {
            String name = "item " + id;
            try {
                ItemStack made = prototypes.create(id);
                report.check(prototypes.idOf(made).filter(id::equals).isPresent(), name + " reverse mapping",
                        "a new stack maps back to its id");
                ItemStack loaded = ItemStack.deserialize(made.serialize());
                report.check(prototypes.idOf(loaded).filter(id::equals).isPresent(), name + " save and load",
                        "survives serialization, which is what chests and relogging do");

                ItemStack decorated = decorate(made);
                ItemStack expanded = prototypes.expand(decorated);
                ItemMeta expandedMeta = expanded.getItemMeta();
                boolean damageable = made.getType().getMaxDurability() > 0;
                boolean damageKept = !damageable || (expandedMeta instanceof Damageable damaged && damaged.getDamage() == 3);
                report.check(expandedMeta.getEnchantLevel(Enchantment.UNBREAKING) == 2 && damageKept
                                && prototypes.idOf(expanded).filter(id::equals).isPresent(),
                        name + " expand keeps player data", "enchantment" + (damageable ? " and damage" : "")
                                + " survive what a creative client sends back");

                if (player == null) {
                    report.skip(name + " creative event", "needs a player sender: run /content selftest in game");
                } else {
                    creativeEvent(report, name, player, id, decorated);
                }
            } catch (RuntimeException error) {
                report.error(name, error);
            }
        }
    }

    private static ItemStack decorate(ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        if (meta instanceof Damageable damageable && copy.getType().getMaxDurability() > 0) damageable.setDamage(3);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Fires the real InventoryCreativeEvent through the plugin manager so the registered listener is what answers. */
    private void creativeEvent(SelfTestReport report, String name, Player player, String id, ItemStack decorated) {
        NamespacedKey tag = new NamespacedKey("flowtail", "id");
        for (boolean stripTag : new boolean[] {false, true}) {
            ItemStack sent = decorated.clone();
            if (stripTag) {
                ItemMeta meta = sent.getItemMeta();
                meta.getPersistentDataContainer().remove(tag);
                sent.setItemMeta(meta);
            }
            InventoryCreativeEvent event = new InventoryCreativeEvent(player.getOpenInventory(),
                    InventoryType.SlotType.QUICKBAR, 0, sent);
            Bukkit.getPluginManager().callEvent(event);
            ItemStack answered = event.getCursor();
            ItemMeta answeredMeta = answered.getItemMeta();
            boolean tagged = answeredMeta.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
            report.check(prototypes.idOf(answered).filter(id::equals).isPresent() && tagged
                            && answeredMeta.getEnchantLevel(Enchantment.UNBREAKING) == 2,
                    name + " creative event" + (stripTag ? " (id tag stripped)" : ""),
                    stripTag ? "recognized by its item_model, id tag restored, enchantment kept"
                            : "recognized, enchantment kept (log shows 'Creative item returned and recognized')");
        }
    }

    // --------------------------------------------------------------- blocks

    private void blocks(SelfTestReport report, CommandSender sender) {
        if (registry.blocks().isEmpty()) { report.skip("blocks", "no blocks are registered"); return; }
        // The first cube block gets the full barrier-projection checks; the first block of every other template gets the shape checks.
        String id = null;
        java.util.Map<BlockShapes.Template, String> shaped = new java.util.LinkedHashMap<>();
        for (String candidate : registry.blocks().keySet()) {
            BlockShapes.Template template = blocks.templateOf(candidate);
            if (!template.shaped()) { if (id == null) id = candidate; } else shaped.putIfAbsent(template, candidate);
        }
        if (id == null) report.skip("cube blocks", "no block with template cube is registered");
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        Location origin = sender instanceof Player player ? player.getLocation() : world.getSpawnLocation();
        List<Block> spots = new ArrayList<>();
        try {
            for (int i = 0; i < (id == null ? 0 : 6) + shaped.size(); i++) {
                Block spot = freeSpot(world, origin, i);
                if (spot == null) {
                    report.skip("blocks", "no free air near " + origin.getBlockX() + "," + origin.getBlockY() + ","
                            + origin.getBlockZ() + ": stand in the open and run it again");
                    return;
                }
                spots.add(spot);
            }
            Block crate = null;
            int cubeSpots = id == null ? 0 : 6;
            if (id != null) {
                crate = spots.get(0);
                Block external = spots.get(1), broken = spots.get(2), ghost = spots.get(3), note = spots.get(4), plain = spots.get(5);
                crate(report, id, crate);
                externalRemoval(report, id, external);
                breakPath(report, id, broken);
                unknownPlugin(report, ghost);
                vanillaBlocks(report, note, plain);
                for (Block spot : spots.subList(1, cubeSpots)) clean(spot);
            }
            int next = cubeSpots;
            for (java.util.Map.Entry<BlockShapes.Template, String> entry : shaped.entrySet()) shape(report, entry.getKey(), entry.getValue(), spots.get(next++));
            if (shaped.isEmpty()) report.skip("shaped blocks", "no slab, stairs, trapdoor, button, pressure plate, carpet or plant block is registered (build examples/shapes-plugin)");
            if (id != null) reload(report, id, crate);
        } catch (RuntimeException error) {
            report.error("blocks", error);
        } finally {
            for (Block spot : spots) {
                try { clean(spot); } catch (RuntimeException error) {
                    plugin.getLogger().warning("selftest cleanup failed at " + spot.getX() + "," + spot.getY() + ","
                            + spot.getZ() + ": " + error.getMessage());
                }
            }
        }
    }

    /** A second legal state for each template, to exercise the carrier-to-display sync. Null for stateless templates. */
    private static java.util.Map<String, String> otherState(BlockShapes.Template template) {
        return switch (template) {
            case SLAB -> java.util.Map.of("type", "top");
            case STAIRS -> java.util.Map.of("facing", "east", "half", "top", "shape", "outer_left");
            case TRAPDOOR -> java.util.Map.of("facing", "west", "half", "top", "open", "true");
            case BUTTON -> java.util.Map.of("face", "floor", "facing", "north", "powered", "true");
            case PRESSURE_PLATE -> java.util.Map.of("powered", "true");
            case SHELF -> java.util.Map.of("facing", "east", "side_chain", "right", "powered", "true");
            default -> null;
        };
    }

    /** Add-only shapes use FlowTail's barrier hitbox + display + click entity; variant state is stored by FlowTail. */
    private void shape(SelfTestReport report, BlockShapes.Template template, String id, Block at) {
        String name = "shaped block " + id + " (" + template.name().toLowerCase() + ")";
        blocks.place(at, id);
        report.check(at.getType() == Material.BARRIER && blocks.idAt(at).filter(id::equals).isPresent(),
                name + " add-only hitbox", "barrier hitbox + saved id record (no vanilla carrier)");
        String variant = blocks.variantOf(id, BlockShapes.format(blocks.stateAt(at)));
        com.google.gson.JsonArray listed = registry.blocks().get(id).has("variants") && registry.blocks().get(id).get("variants").isJsonArray()
                ? registry.blocks().get(id).getAsJsonArray("variants") : new com.google.gson.JsonArray();
        report.check(variant != null && listed.contains(new com.google.gson.JsonPrimitive(variant)), name + " variant in the pack",
                "server chose " + variant + "; the compiler's registry lists " + listed.size() + " variants");
        BlockShapes.Geometry initialGeometry = BlockShapes.geometry(template, blocks.stateAt(at));
        report.check(collisionMatches(at, initialGeometry.collision()), name + " server collision geometry",
                "registered barrier collision matches the FlowTail template state");
        report.check(outlineHits(at, initialGeometry.outline()), name + " server outline geometry",
                "block ray trace hits the saved FlowTail outline");
        BlockManager.Inspection found = blocks.inspect(at, id);
        String expectedModel = id + "/" + variant;
        report.check(found.healthy() && expectedModel.equals(found.displayModel())
                        && "NONE".equals(found.displayTransform()) && Math.abs(found.displayScale() - 1.0f) < 1e-4,
                name + " projection", "display model " + found.displayModel() + " (expected " + expectedModel + "), scale "
                        + found.displayScale() + ", display=" + found.displays() + " interaction=" + found.interactions()
                        + " (expected 1)");

        java.util.Map<String, String> second = otherState(template);
        if (second != null) {
            boolean changed = blocks.updateState(at, second);
            BlockManager.Inspection after = blocks.inspect(at, id);
            String wanted = id + "/" + BlockShapes.variant(template, second);
            report.check(changed && BlockShapes.format(second).equals(BlockShapes.format(blocks.stateAt(at)))
                            && wanted.equals(after.displayModel()) && after.healthy(),
                    name + " updates saved state", "state changed=" + changed + ", display now " + after.displayModel() + " (expected " + wanted + ")");
            BlockShapes.Geometry changedGeometry = BlockShapes.geometry(template, second);
            report.check(collisionMatches(at, changedGeometry.collision()), name + " updates collision geometry",
                    "collision follows the new saved state");
            report.check(outlineHits(at, changedGeometry.outline()), name + " updates outline geometry",
                    "ray trace follows the new saved state");
            // A vanilla block accidentally written at a recorded FlowTail position is repaired back to FlowTail's barrier.
            at.setType(Material.OAK_SLAB, false);
            blocks.repair(at.getChunk());
            report.check(at.getType() == Material.BARRIER && BlockShapes.format(second).equals(BlockShapes.format(blocks.stateAt(at)))
                            && blocks.inspect(at, id).healthy() && collisionMatches(at, changedGeometry.collision()),
                    name + " chunk check restores add-only hitbox", "block=" + at.getType() + ", saved state " + BlockShapes.format(blocks.stateAt(at)));
        }
        at.setType(Material.OAK_SLAB, false); // a vanilla block at a recorded FlowTail position is replaced only by its owned hitbox
        blocks.repair(at.getChunk());
        report.check(at.getType() == Material.BARRIER, name + " hitbox restored", "now " + at.getType());
        Optional<String> removed = blocks.untrack(at);
        report.check(collisionMatches(at, BlockShapes.geometry(BlockShapes.Template.CUBE, java.util.Map.of()).collision()),
                name + " unregisters custom geometry", "untracked barriers return to the default full-cube shape");
        at.setType(Material.AIR, false);
        BlockManager.Inspection left = blocks.inspect(at, id);
        report.check(removed.filter(id::equals).isPresent() && left.displays() == 0 && left.interactions() == 0,
                name + " break", "record and entities are gone");
    }

    private static boolean collisionMatches(Block block, List<BlockShapes.Box> expected) {
        org.bukkit.util.VoxelShape actual = block.getCollisionShape();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            double px = (x + 0.5) / 16.0, py = (y + 0.5) / 16.0, pz = (z + 0.5) / 16.0;
            boolean wanted = false;
            for (BlockShapes.Box box : expected) {
                if (px >= box.minX() && px <= box.maxX() && py >= box.minY() && py <= box.maxY()
                        && pz >= box.minZ() && pz <= box.maxZ()) { wanted = true; break; }
            }
            boolean found = actual.overlaps(new BoundingBox(px - 1e-6, py - 1e-6, pz - 1e-6,
                    px + 1e-6, py + 1e-6, pz + 1e-6));
            if (wanted != found) return false;
        }
        return true;
    }

    private static boolean outlineHits(Block block, List<BlockShapes.Box> outline) {
        if (outline.isEmpty()) return false;
        BlockShapes.Box target = outline.getFirst();
        Location start = new Location(block.getWorld(), block.getX() + (target.minX() + target.maxX()) / 2.0,
                block.getY() + (target.minY() + target.maxY()) / 2.0,
                block.getZ() + target.minZ() - 0.05);
        RayTraceResult hit = block.rayTrace(start, new Vector(0, 0, 1), 2.0, FluidCollisionMode.NEVER);
        return hit != null && hit.getHitBlock() != null && hit.getHitBlock().equals(block);
    }

    private void crate(SelfTestReport report, String id, Block at) {
        String name = "block " + id;
        blocks.place(at, id);
        report.check(at.getType() == Material.BARRIER && blocks.idAt(at).filter(id::equals).isPresent(),
                name + " placed", "barrier hitbox + saved id record");
        BlockManager.Inspection found = blocks.inspect(at, id);
        report.check(found.healthy(), name + " projection", "display=" + found.displays() + " interaction="
                + found.interactions() + " state=" + found.state());
        String expectedModel = prototypes.has(id) ? prototypes.itemModel(id) : id;
        report.check(expectedModel.equals(found.displayModel()), name + " visual model",
                "display shows " + found.displayModel() + ", expected " + expectedModel);
        report.check("NONE".equals(found.displayTransform()) && Math.abs(found.displayScale() - ProjectionSpec.DISPLAY_SCALE) < 1e-4,
                name + " size", "context=" + found.displayTransform() + " scale=" + found.displayScale()
                        + " (exactly one block; the old FIXED context drew it at half size)");
        report.check(near(found.displayOffset(), ProjectionSpec.DISPLAY_OFFSET)
                        && near(found.interactionOffset(), ProjectionSpec.INTERACTION_OFFSET),
                name + " alignment", "display at the block centre, click box on the block floor");
        report.check(Math.abs(found.interactionWidth() - ProjectionSpec.INTERACTION_SIZE) < 1e-4
                        && Math.abs(found.interactionHeight() - ProjectionSpec.INTERACTION_SIZE) < 1e-4,
                name + " click box", "width=" + found.interactionWidth() + " height=" + found.interactionHeight());

        blocks.forget(at.getChunk());
        report.check(blocks.hasStoredRecords(at.getChunk()) && blocks.idAt(at).filter(id::equals).isPresent(),
                name + " saved in chunk", "id read back from the chunk's saved data after dropping the cache");
        report.check(!blocks.ensureProjection(at, id), name + " no needless rebuild",
                "a healthy projection is left alone");

        report.check(blocks.removeDisplayOnly(at) && blocks.inspect(at, id).displays() == 0, name + " damage set up",
                "display entity removed");
        report.check(blocks.ensureProjection(at, id) && blocks.inspect(at, id).healthy(), name + " self-heal",
                "a missing display is rebuilt");
        blocks.duplicateProjection(at, id);
        BlockManager.Inspection doubled = blocks.inspect(at, id);
        report.check(doubled.displays() == 2 && doubled.interactions() == 2, name + " duplicate set up",
                "display=" + doubled.displays() + " interaction=" + doubled.interactions());
        report.check(blocks.ensureProjection(at, id) && blocks.inspect(at, id).healthy(), name + " duplicate cleanup",
                "extra entities removed, exactly one display and one interaction left");
    }

    /** WorldEdit, /setblock or another plugin removes the block but not its record or entities. */
    private void externalRemoval(SelfTestReport report, String id, Block at) {
        String name = "block " + id + " removed behind our back";
        blocks.place(at, id);
        at.setType(Material.AIR, false);
        BlockManager.Repair repair = blocks.repair(at.getChunk());
        BlockManager.Inspection left = blocks.inspect(at, id);
        report.check(repair.stale() >= 1 && blocks.recordedId(at).isEmpty() && left.displays() == 0 && left.interactions() == 0,
                name, "record forgotten and entities removed on the next check (stale=" + repair.stale()
                        + " display=" + left.displays() + " interaction=" + left.interactions() + ")");
    }

    private void breakPath(SelfTestReport report, String id, Block at) {
        String name = "block " + id + " break";
        blocks.place(at, id);
        Optional<String> removed = blocks.untrack(at);
        at.setType(Material.AIR, false);
        BlockManager.Inspection left = blocks.inspect(at, id);
        report.check(removed.filter(id::equals).isPresent() && blocks.recordedId(at).isEmpty()
                        && left.displays() == 0 && left.interactions() == 0,
                name, "record and both entities are gone");
    }

    /** What a block looks like when its plugin is not installed: kept, placeholder shown, never deleted. */
    private void unknownPlugin(SelfTestReport report, Block at) {
        String name = "unknown plugin block";
        blocks.placeUnregistered(at, GHOST);
        BlockManager.Inspection found = blocks.inspect(at, GHOST);
        report.check(found.healthy() && found.state().endsWith("|missing") && found.displayModel().startsWith("none(BARRIER"),
                name + " placeholder", "state=" + found.state() + " display=" + found.displayModel());
        List<String> missing = ContentReport.missing(blocks.idsIn(at.getChunk()), new java.util.HashSet<>(registry.blocks().keySet()));
        report.check(missing.stream().anyMatch(line -> line.startsWith(GHOST)), name + " listed by /content missing",
                missing.isEmpty() ? "nothing listed" : String.join("; ", missing));
        blocks.repair(at.getChunk());
        report.check(blocks.recordedId(at).filter(GHOST::equals).isPresent() && at.getType() == Material.BARRIER,
                name + " kept", "a check never deletes a block whose plugin is missing");
    }

    /** Add-only rule: a vanilla note block and a player-placed barrier are never taken over. */
    private void vanillaBlocks(SelfTestReport report, Block noteSpot, Block barrierSpot) {
        noteSpot.setType(Material.NOTE_BLOCK, false);
        barrierSpot.setType(Material.BARRIER, false);
        report.check(blocks.idAt(noteSpot).isEmpty() && blocks.idAt(barrierSpot).isEmpty(), "vanilla blocks not claimed",
                "an unrecorded note block and barrier are not FlowTail blocks");
        blocks.repair(noteSpot.getChunk());
        blocks.repair(barrierSpot.getChunk());
        report.check(noteSpot.getType() == Material.NOTE_BLOCK, "vanilla note block untouched",
                "still a note block after the chunk check (no carrier migration without a record)");
        report.check(barrierSpot.getType() == Material.BARRIER && blocks.inspect(barrierSpot, "x:y").displays() == 0,
                "player barrier untouched", "still a plain barrier, no entities added");
    }

    /** Unloads and reloads the chunk when the server lets it; the real restart test stays in docs/13 section 6. */
    private void reload(SelfTestReport report, String id, Block at) {
        String name = "block " + id + " chunk reload";
        Chunk chunk = at.getChunk();
        int cx = chunk.getX(), cz = chunk.getZ();
        World world = at.getWorld();
        boolean asked = chunk.unload(true);
        if (!asked || world.isChunkLoaded(cx, cz)) {
            report.skip(name, "the server keeps this chunk loaded (player, spawn area or deferred unload): "
                    + "stand away from it or use the restart test in docs/13 section 6");
            return;
        }
        Chunk reloaded = world.getChunkAt(cx, cz);
        BlockManager.Inspection found = blocks.inspect(reloaded.getBlock(at.getX() & 15, at.getY(), at.getZ() & 15), id);
        report.check(blocks.idAt(at).filter(id::equals).isPresent() && found.healthy(), name,
                "id record and both entities came back from disk (display=" + found.displays()
                        + " interaction=" + found.interactions() + ")");
    }

    // -------------------------------------------------------------- helpers

    /** Air a few blocks from the origin, spread along x so each spot is independent. Null when none is free. */
    private static Block freeSpot(World world, Location origin, int index) {
        int x = origin.getBlockX() + 3 + 2 * index, z = origin.getBlockZ() + 3;
        int baseY = Math.max(world.getMinHeight() + 1, origin.getBlockY());
        for (int dy = 0; dy < 4; dy++) {
            int y = baseY + dy;
            if (y >= world.getMaxHeight() - 1) break;
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) return block;
        }
        return null;
    }

    /** Removes whatever the test left at a spot: record, entities and block. Safe on an untouched spot. */
    private void clean(Block spot) {
        blocks.dropRecord(spot);
        blocks.clearProjection(spot);
        if (!spot.getType().isAir()) spot.setType(Material.AIR, false);
    }

    private static boolean near(double[] actual, double[] expected) {
        for (int i = 0; i < 3; i++) if (Math.abs(actual[i] - expected[i]) > 1e-3) return false;
        return true;
    }
}
