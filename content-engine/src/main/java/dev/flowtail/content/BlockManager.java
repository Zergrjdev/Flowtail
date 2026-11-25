package dev.flowtail.content;

import com.google.gson.JsonObject;
import dev.flowtail.content.BlockShapes.Template;
import dev.flowtail.content.api.FlowTailBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Add-only block projection: saved ids and display state + a FlowTail-owned barrier hitbox + item-display visual +
 * interaction entity. Vanilla block states are never used as shape carriers. Legacy shape carriers are recognized
 * only so recorded FlowTail positions can be migrated to barriers on chunk load.
 */
final class BlockManager implements FlowTailBlocks {
    static final Material HITBOX = Material.BARRIER;
    private static final Material LEGACY_CARRIER = Material.NOTE_BLOCK;
    private static final NamespacedKey RECORDS = new NamespacedKey("flowtail", "blocks");
    private static final NamespacedKey ENTITY_KIND = new NamespacedKey("flowtail", "projection_kind");
    private static final NamespacedKey ENTITY_POS = new NamespacedKey("flowtail", "projection_pos");
    private static final NamespacedKey ENTITY_STATE = new NamespacedKey("flowtail", "projection_state");
    private static Set<Material> carrierMaterials;
    private record ChunkId(UUID world, long key) {}
    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final ItemPrototypes items;
    private final Map<ChunkId, BlockRecords> cache = new HashMap<>();
    private final Map<String, String> stagedStates = new HashMap<>();

    BlockManager(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes items) {
        this.plugin = plugin; this.registry = registry; this.items = items;
    }

    int projectionCount(Chunk chunk) { return records(chunk).size(); }

    /** Barrier hitbox and historical carriers, accepted only when a saved FlowTail id record owns the position. */
    static synchronized Set<Material> carriers() {
        if (carrierMaterials == null) {
            Set<Material> found = new HashSet<>();
            for (String name : LegacyShapeCarriers.allMaterials()) {
                Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (material != null) found.add(material);
            }
            found.add(HITBOX);
            carrierMaterials = found;
        }
        return carrierMaterials;
    }

    static boolean isCarrier(Block block) { return carriers().contains(block.getType()); }

    /** The template of a registered block; a cube for unknown ids and for templates that are not built. */
    Template templateOf(String id) {
        JsonObject definition = registry.blocks().get(id);
        return definition != null && definition.has("template") && definition.get("template").isJsonPrimitive()
                ? Template.of(definition.get("template").getAsString()) : Template.CUBE;
    }

    private String presetOf(String id) {
        JsonObject definition = registry.blocks().get(id);
        return definition != null && definition.has("sounds") && definition.get("sounds").isJsonObject()
                && definition.getAsJsonObject("sounds").has("preset")
                ? definition.getAsJsonObject("sounds").get("preset").getAsString() : null;
    }

    /** The model variant a state shows; null for cubes and for ids whose plugin is gone. A bad saved state falls back to the default state. */
    String variantOf(String id, String stateText) {
        if (!registry.blocks().containsKey(id)) return null;
        Template template = templateOf(id);
        if (!template.shaped()) return null;
        Map<String, String> state = BlockShapes.parse(stateText);
        if (!BlockShapes.valid(template, state)) state = BlockShapes.defaultState(template);
        return BlockShapes.variant(template, state);
    }

    /** Whether the block at this projection has an interaction entity: cubes and plants, and every placeholder. */
    private boolean wantsInteraction(String id) {
        return !registry.blocks().containsKey(id) || templateOf(id).hasInteractionBox();
    }

    /** The state prepared by FlowTail placement for the synchronous BlockPlaceEvent, if any. */
    void stageState(Block block, String id, Map<String, String> state) {
        stagedStates.put(pos(block), id + "|" + BlockShapes.format(state));
    }
    void clearStagedState(Block block) { stagedStates.remove(pos(block)); }

    /** The display state saved for the block at a position (empty map for cubes and unknown blocks). */
    Map<String, String> stateAt(Block block) {
        return BlockShapes.parse(records(block.getChunk()).state(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    void place(Block block, String id) {
        if (!registry.blocks().containsKey(id)) throw new IllegalArgumentException("unknown block id " + id);
        Chunk chunk = block.getChunk();
        BlockRecords data = records(chunk);
        Template template = templateOf(id);
        String stateText = "";
        if (template.shaped()) {
            String staged = stagedStates.remove(pos(block));
            Map<String, String> state = staged != null && staged.startsWith(id + "|")
                    ? BlockShapes.parse(staged.substring(id.length() + 1))
                    : BlockShapes.stateFromCarrier(template, block.getBlockData().getAsString())
                            .orElseGet(() -> BlockShapes.defaultState(template));
            if (!BlockShapes.valid(template, state)) state = BlockShapes.defaultState(template);
            stateText = BlockShapes.format(state);
        }
        block.setType(HITBOX, false);
        data.put(block.getX() & 15, block.getY(), block.getZ() & 15, id, stateText);
        save(chunk, data);
        removeProjection(chunk, pos(block)); // leftovers of an earlier block at this spot (external removal)
        project(block, id, stateText);
    }

    @Override public Optional<String> idAt(Block block) {
        if (!isCarrier(block)) return Optional.empty();
        String id = records(block.getChunk()).get(block.getX() & 15, block.getY(), block.getZ() & 15);
        return Optional.ofNullable(id);
    }

    Optional<String> untrack(Block block) {
        Optional<String> found = idAt(block);
        if (found.isEmpty()) return found;
        Chunk chunk = block.getChunk();
        BlockRecords data = records(chunk);
        data.remove(block.getX() & 15, block.getY(), block.getZ() & 15);
        save(chunk, data);
        Bukkit.getServer().clearCustomBlockShape(plugin, block);
        removeProjection(chunk, pos(block));
        return found;
    }

    /**
     * On load, migrate only recorded FlowTail positions; an ordinary player note block has no record and is
     * untouched. Also rebuilds stale projections (old layout, plugin added/removed since) and removes projection
     * entities that no record owns any more.
     */
    Repair repair(Chunk chunk) {
        BlockRecords data = records(chunk);
        // No records: never force-load the chunk's entities just to look (that would cost every chunk load).
        // Entities that are already loaded are still swept for orphans.
        boolean needsCheck = ProjectionSpec.chunkNeedsCheck(data.size(), data.unparsed().size());
        Map<String, List<Entity>> index = needsCheck || chunk.isEntitiesLoaded()
                ? indexProjections(chunk) : new HashMap<>();
        List<int[]> stale = new ArrayList<>();
        List<String[]> updates = new ArrayList<>(); // {x, y, z, id, new state}
        Set<String> owned = new HashSet<>();
        int[] counts = {0, 0}; // migrated, rebuilt
        int baseX = chunk.getX() << 4, baseZ = chunk.getZ() << 4;
        data.forEachWithState((x, y, z, id, stateText) -> {
            Block block = chunk.getBlock(x, y, z);
            String position = (baseX + x) + "," + y + "," + (baseZ + z);
            if (!isCarrier(block)) {
                removeAll(index.remove(position));
                stale.add(new int[] {x, y, z});
                return;
            }
            owned.add(position);
            String current = stateText;
            boolean known = registry.blocks().containsKey(id);
            Template template = templateOf(id);
            if (known && template.shaped() && !BlockShapes.valid(template, BlockShapes.parse(stateText))) {
                current = BlockShapes.format(BlockShapes.defaultState(template));
                updates.add(new String[] {String.valueOf(x), String.valueOf(y), String.valueOf(z), id, current});
            }
            if (block.getType() != HITBOX) {
                // Only a saved FlowTail id record reaches this path; convert its old vanilla carrier to our hitbox.
                block.setType(HITBOX, false);
                counts[0]++;
            }
            setShapes(block, id, current);
            if (ensure(block, id, current, position, index.get(position))) counts[1]++;
        });
        for (String[] update : updates) data.put(Integer.parseInt(update[0]), Integer.parseInt(update[1]), Integer.parseInt(update[2]), update[3], update[4]);
        if (!updates.isEmpty()) save(chunk, data);
        for (int[] at : stale) data.remove(at[0], at[1], at[2]);
        if (!stale.isEmpty()) save(chunk, data);
        int orphans = 0;
        if (data.unparsed().isEmpty()) { // never delete entities next to records we could not read
            for (Map.Entry<String, List<Entity>> entry : index.entrySet()) {
                if (owned.contains(entry.getKey())) continue;
                orphans += entry.getValue().size();
                removeAll(entry.getValue());
            }
        }
        return new Repair(counts[0], stale.size(), counts[1], orphans);
    }

    /** migrated legacy note blocks, records forgotten (block gone), projections rebuilt, orphan entities removed. */
    record Repair(int migrated, int stale, int rebuilt, int orphans) {
        boolean changedAnything() { return migrated > 0 || stale > 0 || rebuilt > 0 || orphans > 0; }
    }
    java.util.Collection<String> idsIn(Chunk chunk) { return records(chunk).ids(); }
    void forget(Chunk chunk) { cache.remove(new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey())); }

    /** The id recorded for the block this projection entity belongs to, or null when it is not (or no longer) one. */
    String idForProjection(Entity entity) {
        return projectedBlock(entity).flatMap(this::idAt).orElse(null);
    }

    /** The block a projection entity stands for; empty for any other entity or unreadable markers. */
    Optional<Block> projectedBlock(Entity entity) {
        String position = entity.getPersistentDataContainer().get(ENTITY_POS, PersistentDataType.STRING);
        if (position == null) return Optional.empty();
        String[] parts = position.split(",", -1);
        if (parts.length != 3) return Optional.empty();
        try {
            return Optional.of(entity.getWorld().getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    /** True for any entity FlowTail spawned for a projection, whether or not its block still exists. */
    boolean isProjectionEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(ENTITY_POS, PersistentDataType.STRING);
    }

    /** Removes a projection entity whose block is gone (self-heal for external edits such as WorldEdit). */
    void removeStale(Entity entity) { entity.remove(); }

    /** What is physically standing at a projection, read back from the real entities (for inspect and the self-test). */
    record Inspection(int displays, int interactions, String state, String displayModel, String displayTransform,
                      float displayScale, double[] displayOffset, float interactionWidth, float interactionHeight,
                      double[] interactionOffset, boolean healthy) {}

    Inspection inspect(Block block, String id) {
        Chunk chunk = block.getChunk();
        String position = pos(block);
        int displays = 0, interactions = 0;
        String state = "none", model = "none", transform = "none";
        float scale = 0f, width = 0f, height = 0f;
        double[] displayOffset = new double[3], interactionOffset = new double[3];
        for (Entity entity : chunk.getEntities()) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (!position.equals(pdc.get(ENTITY_POS, PersistentDataType.STRING))) continue;
            String kind = pdc.get(ENTITY_KIND, PersistentDataType.STRING);
            Location at = entity.getLocation();
            double[] offset = {at.getX() - block.getX(), at.getY() - block.getY(), at.getZ() - block.getZ()};
            if ("display".equals(kind) && entity instanceof ItemDisplay display) {
                displays++;
                state = String.valueOf(pdc.get(ENTITY_STATE, PersistentDataType.STRING));
                displayOffset = offset;
                transform = String.valueOf(display.getItemDisplayTransform());
                scale = display.getTransformation().getScale().x();
                ItemMeta meta = display.getItemStack().getItemMeta();
                NamespacedKey shown = meta == null ? null : meta.getItemModel();
                model = shown == null ? "none(" + display.getItemStack().getType() + ")" : shown.toString();
            } else if ("interaction".equals(kind) && entity instanceof Interaction interaction) {
                interactions++;
                interactionOffset = offset;
                width = interaction.getInteractionWidth();
                height = interaction.getInteractionHeight();
            }
        }
        boolean healthy = displays == 1 && interactions == (wantsInteraction(id) ? 1 : 0)
                && !ProjectionSpec.displayIsStale(state, id, registry.blocks().containsKey(id),
                        variantOf(id, records(chunk).state(block.getX() & 15, block.getY(), block.getZ() & 15)));
        return new Inspection(displays, interactions, state, model, transform, scale, displayOffset, width, height,
                interactionOffset, healthy);
    }

    /** One-line state of the projection at a block, for {@code /content inspect}. */
    String describeProjection(Block block, String id) {
        Inspection found = inspect(block, id);
        return "display=" + found.displays() + " interaction=" + found.interactions() + " state=" + found.state()
                + (found.healthy() ? " (healthy)" : " (needs rebuild on next chunk load)");
    }

    /** True when the chunk's saved record text contains anything (parsed or not); reads the stored data, not the cache. */
    boolean hasStoredRecords(Chunk chunk) {
        String text = chunk.getPersistentDataContainer().get(RECORDS, PersistentDataType.STRING);
        return text != null && !text.isBlank();
    }

    /** True when the chunk has anything for {@link #repair} to look at. Cheap: no entity access. */
    boolean chunkNeedsCheck(Chunk chunk) {
        BlockRecords data = records(chunk);
        return ProjectionSpec.chunkNeedsCheck(data.size(), data.unparsed().size());
    }

    /**
     * Self-test only: records and projects an id the registry does not know, exactly as a block whose plugin was
     * removed looks after a restart. Refuses ids the registry knows so it can never fake real content.
     */
    void placeUnregistered(Block block, String id) {
        if (registry.blocks().containsKey(id)) throw new IllegalArgumentException(id + " is registered; use place()");
        Chunk chunk = block.getChunk();
        BlockRecords data = records(chunk);
        data.put(block.getX() & 15, block.getY(), block.getZ() & 15, id);
        save(chunk, data);
        block.setType(HITBOX, false);
        removeProjection(chunk, pos(block));
        project(block, id, "");
    }

    /** Self-test only: spawns a second projection on top of the first, like a crash or a bad paste could. */
    void duplicateProjection(Block block, String id) { project(block, id, records(block.getChunk()).state(block.getX() & 15, block.getY(), block.getZ() & 15)); }

    /** Self-test only: removes just the display entity at a block (damage to repair). */
    boolean removeDisplayOnly(Block block) {
        for (Entity entity : block.getChunk().getEntities()) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (pos(block).equals(pdc.get(ENTITY_POS, PersistentDataType.STRING))
                    && "display".equals(pdc.get(ENTITY_KIND, PersistentDataType.STRING))) { entity.remove(); return true; }
        }
        return false;
    }

    /** Makes sure one block has exactly one fresh projection; true when it had to rebuild it. */
    boolean ensureProjection(Block block, String id) {
        Chunk chunk = block.getChunk();
        String position = pos(block);
        return ensure(block, id, records(chunk).state(block.getX() & 15, block.getY(), block.getZ() & 15), position,
                indexProjections(chunk).get(position));
    }

    /**
     * Reads a shaped block's carrier back into the saved state and the display (redstone toggled a trapdoor, a
     * neighbouring stair changed its corner, a plate was stepped on). True when something changed. Main thread.
     */
    boolean sync(Block block) {
        return false; // vanilla logic never owns or changes the state of a FlowTail shape
    }

    /** Replaces a shaped block's saved display state and refreshes its projection. */
    boolean updateState(Block block, Map<String, String> state) {
        Optional<String> found = idAt(block);
        if (found.isEmpty()) return false;
        String id = found.get();
        Template template = templateOf(id);
        if (!registry.blocks().containsKey(id) || !template.shaped() || !BlockShapes.valid(template, state)) return false;
        String text = BlockShapes.format(state);
        Chunk chunk = block.getChunk();
        BlockRecords data = records(chunk);
        if (text.equals(data.state(block.getX() & 15, block.getY(), block.getZ() & 15))) return false;
        data.put(block.getX() & 15, block.getY(), block.getZ() & 15, id, text);
        save(chunk, data);
        setShapes(block, id, text);
        if (!updateDisplay(block, id, text)) ensureProjection(block, id);
        return true;
    }

    /** Swaps the model of the existing display without respawning it (no flicker). False when no display was found. */
    private boolean updateDisplay(Block block, String id, String stateText) {
        String position = pos(block);
        String variant = variantOf(id, stateText);
        for (Entity entity : block.getWorld().getNearbyEntities(BoundingBox.of(block).expand(0.25), e -> e instanceof ItemDisplay)) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (!position.equals(pdc.get(ENTITY_POS, PersistentDataType.STRING))) continue;
            ((ItemDisplay) entity).setItemStack(displayStack(id, true, variant));
            pdc.set(ENTITY_STATE, PersistentDataType.STRING, ProjectionSpec.stateKey(id, true, variant));
            return true;
        }
        return false;
    }

    /** The id saved for this position whatever the block there is now (for checks); empty when no record. */
    Optional<String> recordedId(Block block) {
        return Optional.ofNullable(records(block.getChunk()).get(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    /** Forgets the record at a position whatever block is there now (self-test cleanup); false when there was none. */
    boolean dropRecord(Block block) {
        Chunk chunk = block.getChunk();
        BlockRecords data = records(chunk);
        boolean had = data.remove(block.getX() & 15, block.getY(), block.getZ() & 15) != null;
        if (had) {
            save(chunk, data);
            Bukkit.getServer().clearCustomBlockShape(plugin, block);
        }
        return had;
    }

    /** Removes every projection entity at a position without touching the record (self-test cleanup). */
    void clearProjection(Block block) { removeProjection(block.getChunk(), pos(block)); }

    /** Makes the projection at {@code position} exactly one fresh display + one interaction; true when it rebuilt. */
    private boolean ensure(Block block, String id, String stateText, String position, List<Entity> existing) {
        boolean known = registry.blocks().containsKey(id);
        String variant = variantOf(id, stateText);
        boolean wantsInteraction = wantsInteraction(id);
        ItemDisplay display = null;
        Interaction interaction = null;
        int others = 0;
        if (existing != null) {
            for (Entity entity : existing) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                String kind = pdc.get(ENTITY_KIND, PersistentDataType.STRING);
                if ("display".equals(kind) && entity instanceof ItemDisplay foundDisplay && display == null) display = foundDisplay;
                else if ("interaction".equals(kind) && wantsInteraction && entity instanceof Interaction foundInteraction && interaction == null) interaction = foundInteraction;
                else others++; // duplicate or unknown kind at this position
            }
        }
        boolean healthy = display != null && (interaction != null) == wantsInteraction && others == 0
                && !ProjectionSpec.displayIsStale(display.getPersistentDataContainer()
                        .get(ENTITY_STATE, PersistentDataType.STRING), id, known, variant);
        if (healthy) return false;
        removeAll(existing);
        project(block, id, stateText);
        return true;
    }

    private void project(Block block, String id, String stateText) {
        setShapes(block, id, stateText);
        World world = block.getWorld();
        boolean known = registry.blocks().containsKey(id);
        Template template = templateOf(id);
        boolean shaped = known && template.shaped();
        String variant = variantOf(id, stateText);
        String position = pos(block);
        String state = ProjectionSpec.stateKey(id, known, variant);
        Location displayAt = block.getLocation().add(ProjectionSpec.DISPLAY_OFFSET[0], ProjectionSpec.DISPLAY_OFFSET[1],
                ProjectionSpec.DISPLAY_OFFSET[2]);
        Location interactionAt = block.getLocation().add(ProjectionSpec.INTERACTION_OFFSET[0],
                ProjectionSpec.INTERACTION_OFFSET[1], ProjectionSpec.INTERACTION_OFFSET[2]);
        world.spawn(displayAt, ItemDisplay.class, entity -> {
            entity.setItemStack(displayStack(id, known, variant));
            // NONE = no display transform: a model fills exactly its own block coordinates. (FIXED shrank it to half size.)
            entity.setItemDisplayTransform(ItemDisplayTransform.NONE);
            float scale = ProjectionSpec.displayScale(shaped);
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            entity.setGravity(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setPersistent(true);
            entity.setViewRange(1.0f); entity.setShadowRadius(0);
            mark(entity, "display", position, state);
        });
        if (!wantsInteraction(id)) return; // real carriers (slab, stairs...) are targeted by the client directly
        boolean plant = known && template == Template.PLANT;
        world.spawn(interactionAt, Interaction.class, entity -> {
            entity.setInteractionWidth(plant ? ProjectionSpec.PLANT_BOX_WIDTH : ProjectionSpec.INTERACTION_SIZE);
            entity.setInteractionHeight(plant ? ProjectionSpec.PLANT_BOX_HEIGHT : ProjectionSpec.INTERACTION_SIZE);
            entity.setResponsive(true);
            entity.setGravity(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setPersistent(true);
            mark(entity, "interaction", position, state);
        });
    }

    private void setShapes(Block block, String id, String stateText) {
        Template template = templateOf(id);
        Map<String, String> state = BlockShapes.parse(stateText);
        if (template.shaped() && !BlockShapes.valid(template, state)) state = BlockShapes.defaultState(template);
        BlockShapes.Geometry geometry = BlockShapes.geometry(template, state);
        Bukkit.getServer().setCustomBlockShapes(plugin, block, toBoxes(geometry.collision()), toBoxes(geometry.outline()));
    }

    private static List<BoundingBox> toBoxes(List<BlockShapes.Box> boxes) {
        List<BoundingBox> converted = new ArrayList<>(boxes.size());
        for (BlockShapes.Box box : boxes) converted.add(new BoundingBox(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()));
        return converted;
    }

    /**
     * What the display entity shows: the block's own item while the plugin is installed (also for blocks with
     * {@code item.auto=false}, which have a model but no giveable item), a barrier icon as placeholder otherwise.
     */
    private ItemStack displayStack(String id, boolean known, String variant) {
        if (!known) return new ItemStack(Material.BARRIER);
        ItemStack stack;
        if (items.has(id)) stack = items.create(id);
        else stack = new ItemStack(Material.PAPER);
        // A shaped block shows its variant model (pack: items/<id>/<variant>.json); a cube shows the model of its own id.
        NamespacedKey model = NamespacedKey.fromString(variant == null ? id : id + "/" + variant);
        if (model != null) {
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(model);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void mark(Entity entity, String kind, String position, String state) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(ENTITY_KIND, PersistentDataType.STRING, kind);
        pdc.set(ENTITY_POS, PersistentDataType.STRING, position);
        pdc.set(ENTITY_STATE, PersistentDataType.STRING, state);
    }

    private Map<String, List<Entity>> indexProjections(Chunk chunk) {
        Map<String, List<Entity>> index = new HashMap<>();
        for (Entity entity : chunk.getEntities()) { // forces entity loading for this chunk if needed
            String position = entity.getPersistentDataContainer().get(ENTITY_POS, PersistentDataType.STRING);
            if (position != null) index.computeIfAbsent(position, key -> new ArrayList<>()).add(entity);
        }
        return index;
    }

    private void removeAll(List<Entity> entities) {
        if (entities != null) for (Entity entity : entities) entity.remove();
    }

    private void removeProjection(Chunk chunk, String position) {
        removeAll(indexProjections(chunk).get(position));
    }

    private static String pos(Block block) { return block.getX() + "," + block.getY() + "," + block.getZ(); }

    private BlockRecords records(Chunk chunk) {
        ChunkId id = new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey());
        BlockRecords cached = cache.get(id);
        if (cached != null) return cached;
        String text = chunk.getPersistentDataContainer().get(RECORDS, PersistentDataType.STRING);
        BlockRecords decoded = BlockRecords.decode(text);
        if (!decoded.unparsed().isEmpty()) plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ()
                + " in " + chunk.getWorld().getName() + " has unreadable custom block records; preserving them");
        cache.put(id, decoded);
        return decoded;
    }

    private void save(Chunk chunk, BlockRecords data) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (data.isEmpty()) pdc.remove(RECORDS); else pdc.set(RECORDS, PersistentDataType.STRING, data.encode());
    }
}
