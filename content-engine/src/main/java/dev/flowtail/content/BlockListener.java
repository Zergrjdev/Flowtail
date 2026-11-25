package dev.flowtail.content;

import com.google.gson.JsonObject;
import dev.flowtail.content.api.FlowTailBlockInteractEvent;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import dev.flowtail.content.BlockShapes.Template;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Place / break / interact with FlowTail cube projections while preserving ordinary vanilla block behavior.
 */
final class BlockListener implements Listener {
    private final FlowTailContentPlugin plugin;
    private final ContentRegistry registry;
    private final ItemPrototypes prototypes;
    private final BlockManager blocks;
    private final BlockPlacer placer;
    private final MiningSessions mining;
    /** Face from the last {@link PlayerInteractAtEntityEvent} of each player (it arrives just before the plain event). */
    private final Map<UUID, ProjectionSpec.Face> lastClickedFace = new HashMap<>();
    /** Chunks that loaded with FlowTail records and wait for their next-tick projection check. */
    private final Set<Chunk> pendingChunks = new LinkedHashSet<>();

    BlockListener(FlowTailContentPlugin plugin, ContentRegistry registry, ItemPrototypes prototypes,
                  BlockManager blocks) {
        this.plugin = plugin; this.registry = registry; this.prototypes = prototypes;
        this.blocks = blocks;
        this.placer = new BlockPlacer(registry, blocks);
        this.mining = new MiningSessions(plugin, registry, prototypes, blocks, this::punchBreak);
        Bukkit.getPluginManager().registerEvents(mining, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placer.forget(event.getPlayer().getUniqueId());
        lastClickedFace.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<String> id = prototypes.idOf(event.getItemInHand());
        if (id.isPresent() && registry.blocks().containsKey(id.get())) {
            try {
                blocks.place(event.getBlockPlaced(), id.get());
                plugin.getLogger().info("Placed custom block plugin_id=" + owner(id.get()) + " content_id=" + id.get()
                        + " at=" + where(event.getBlockPlaced()) + " player=" + event.getPlayer().getUniqueId()
                        + (blocks.templateOf(id.get()).shaped() ? " state=" + BlockShapes.format(blocks.stateAt(event.getBlockPlaced())) : ""));
                placer.refreshStairsAround(event.getBlockPlaced());
            } catch (RuntimeException error) {
                event.setCancelled(true);
                plugin.getLogger().severe("Could not place custom block plugin_id=" + owner(id.get())
                        + " content_id=" + id.get() + ": " + error.getMessage());
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<String> id = blocks.untrack(block);
        if (id.isEmpty()) return;
        event.setDropItems(false);
        Bukkit.getScheduler().runTask(plugin, () -> placer.refreshStairsAround(block));
        JsonObject definition = registry.blocks().get(id.get());
        if (definition == null) {
            plugin.getLogger().warning("Broke placeholder for unknown custom block plugin_id=" + owner(id.get())
                    + " content_id=" + id.get() + " at=" + where(block) + " (its plugin is not installed; nothing dropped)");
            return;
        }
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE && mining.dropsFor(event.getPlayer(), id.get())) dropSelf(block, id.get(), definition);
        breakEffects(block, id.get(), definition);
        plugin.getLogger().info("Broke custom block plugin_id=" + owner(id.get()) + " content_id=" + id.get()
                + " at=" + where(block) + " player=" + event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Optional<String> id = blocks.idAt(block);
        if (id.isEmpty()) return;
        // Vanilla physics must never change a FlowTail-owned hitbox or its separate saved state.
        event.setCancelled(true);
    }

    /** A mob or item stepping on a FlowTail pressure plate is not yet wired to redstone output. */

    /** Fire never burns a FlowTail block: its carrier (an oak slab, say) is flammable but the block is not. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (blocks.idAt(event.getBlock()).isPresent()) event.setCancelled(true); }

    /** Water flows into slabs, stairs and trapdoors (waterlogging) like vanilla; it never washes away buttons, plates, carpets or plants. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Optional<String> id = blocks.idAt(event.getToBlock());
        if (id.isEmpty()) return;
        Template template = blocks.templateOf(id.get());
        if (template.shaped() && !template.waterloggable()) event.setCancelled(true);
    }

    /**
     * Vanilla is about to destroy a carrier by itself (its support went away, a mob changed it, a command): drop the
     * FlowTail item, not the carrier's vanilla item, and forget the block. Cancelled when the plugin is not installed
     * so an unknown block is never lost.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        Optional<String> found = blocks.idAt(block);
        if (found.isEmpty()) return;
        JsonObject definition = registry.blocks().get(found.get());
        if (definition == null) { event.setCancelled(true); return; }
        blocks.untrack(block);
        event.setWillDrop(false);
        dropSelf(block, found.get(), definition);
        plugin.getLogger().info("Custom block destroyed by the world (lost support or similar) plugin_id=" + owner(found.get())
                + " content_id=" + found.get() + " at=" + where(block));
        Bukkit.getScheduler().runTask(plugin, () -> placer.refreshStairsAround(block));
    }

    /**
     * Heals worlds saved before the guard existed (and damage done while the plugin was off). Two rules keep this
     * free for ordinary chunks: a chunk without any FlowTail record is ignored here (no entity access at all), and the
     * check for a chunk with records runs on the next tick, never inside the load event, where Paper's entities of
     * that chunk may not be loaded yet.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        Chunk chunk = event.getChunk();
        if (!blocks.chunkNeedsCheck(chunk)) return;
        boolean first = pendingChunks.isEmpty();
        pendingChunks.add(chunk);
        if (first) Bukkit.getScheduler().runTask(plugin, () -> checkPendingChunks());
    }

    private void checkPendingChunks() {
        List<Chunk> batch = new ArrayList<>(pendingChunks);
        pendingChunks.clear();
        for (Chunk chunk : batch) {
            if (!chunk.isLoaded()) continue; // unloaded again before its turn: nothing to heal, records stay in the chunk
            BlockManager.Repair repair = blocks.repair(chunk);
            if (repair.changedAnything()) {
                plugin.getLogger().info("Checked custom blocks in chunk " + chunk.getX() + "," + chunk.getZ()
                        + " world=" + chunk.getWorld().getName() + " migrated=" + repair.migrated()
                        + " forgotten_missing=" + repair.stale() + " projections_rebuilt=" + repair.rebuilt()
                        + " orphan_entities_removed=" + repair.orphans());
            }
        }
    }

    /** FlowTail clicks are relayed to plugins; the barrier has no inherited vanilla use action. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        EquipmentSlot hand = event.getHand();
        if (block != null && event.getAction() == Action.PHYSICAL) return;
        if (block == null || hand == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Optional<String> clicked = blocks.idAt(block);
        ItemStack held = event.getItem();
        boolean holding = held != null && !held.getType().isAir();
        boolean sneakingWithItem = InteractRules.bypassesBlockUse(player.isSneaking(), holding);

        // The barrier is only a hitbox. It has no vanilla use action, so held vanilla items keep their ordinary
        // placement/use behavior against it.
        boolean itemUsedNormally = clicked.isPresent() && holding && !player.isSneaking();
        // Trapdoor and button state changes are managed by FlowTail and persisted with the content id.
        boolean templateUse = clicked.isPresent() && registry.blocks().containsKey(clicked.get())
                && blocks.templateOf(clicked.get()).interactive();
        if (clicked.isPresent() && !itemUsedNormally && !templateUse) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        if (clicked.isPresent() && !sneakingWithItem && registry.blocks().containsKey(clicked.get())) {
            FlowTailBlockInteractEvent api = new FlowTailBlockInteractEvent(player, block, clicked.get(), hand,
                    holding ? held : null, event.getBlockFace());
            Bukkit.getPluginManager().callEvent(api);
            if (api.isCancelled()) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                return;
            }
        }

        if (clicked.isPresent() && (!holding || player.isSneaking()) && activateShape(block, clicked.get())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }

        if (!holding) return;
        Optional<String> itemId = prototypes.idOf(held);
        boolean customBlockItem = itemId.isPresent() && registry.blocks().containsKey(itemId.get());
        if (customBlockItem && !prototypes.baseOf(itemId.get()).isBlock()) {
            boolean vanillaTakesClick = clicked.isEmpty() && InteractRules.vanillaBlockTakesClick(
                    block.getType().isInteractable(), event.useInteractedBlock() == Event.Result.DENY, sneakingWithItem);
            if (vanillaTakesClick) return;
            if (placer.place(player, block, event.getBlockFace(), itemId.get(), held, hand, event.getInteractionPoint())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Custom display-only state change; it never invokes a vanilla block action. */
    private boolean activateShape(Block block, String id) {
        Template template = blocks.templateOf(id);
        Map<String, String> state = new HashMap<>(blocks.stateAt(block));
        if (template == Template.TRAPDOOR) {
            state.put("open", String.valueOf(!Boolean.parseBoolean(state.getOrDefault("open", "false"))));
            blocks.updateState(block, state);
            return true;
        }
        if (template == Template.BUTTON) {
            state.put("powered", "true");
            blocks.updateState(block, state);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (blocks.idAt(block).filter(id::equals).isEmpty()) return;
                Map<String, String> current = new HashMap<>(blocks.stateAt(block));
                if (!"true".equals(current.get("powered"))) return;
                current.put("powered", "false");
                blocks.updateState(block, current);
            }, 20L);
            return true;
        }
        return false;
    }

    /** Pressure-plate display state follows players entering and leaving its top face. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) return;
        Block oldPlate = event.getFrom().getBlock().getRelative(BlockFace.DOWN);
        Block newPlate = event.getTo().getBlock().getRelative(BlockFace.DOWN);
        if (!oldPlate.equals(newPlate)) setPlatePower(oldPlate, false);
        setPlatePower(newPlate, true);
    }

    private void setPlatePower(Block block, boolean powered) {
        Optional<String> found = blocks.idAt(block);
        if (found.isEmpty() || blocks.templateOf(found.get()) != Template.PRESSURE_PLATE) return;
        Map<String, String> state = new HashMap<>(blocks.stateAt(block));
        if (String.valueOf(powered).equals(state.get("powered"))) return;
        state.put("powered", String.valueOf(powered));
        blocks.updateState(block, state);
    }

    /**
     * Survival mining start (P3-04: {@link MiningSessions} times the break from the block's mining section; a block with no mining section still breaks instantly, as in Phase 2). A barrier is unbreakable in
     * survival, so the dig start is intercepted: the block use is denied and the break is done next tick through a
     * normal {@link BlockBreakEvent} (protection plugins can veto). Creative needs nothing here: the client breaks the
     * barrier and {@link #onBreak} cleans up.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunch(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        if (blocks.idAt(block).isEmpty()) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        mining.begin(event.getPlayer(), block);
    }

    /**
     * Left-click on the projection's interaction entity (when the client targets it instead of the barrier). Never
     * damages the entity; breaks the block like {@link #onPunch}. Paper fires this event cancelled for entities it
     * will not attack, so it is deliberately not {@code ignoreCancelled}.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectionAttack(PrePlayerAttackEntityEvent event) {
        Entity entity = event.getAttacked();
        if (!blocks.isProjectionEntity(entity)) return;
        event.setCancelled(true);
        Optional<Block> block = blocks.projectedBlock(entity);
        if (block.isEmpty() || blocks.idAt(block.get()).isEmpty()) { blocks.removeStale(entity); return; }
        GameMode mode = event.getPlayer().getGameMode();
        if (mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR) return;
        mining.begin(event.getPlayer(), block.get());
    }

    private void punchBreak(Player player, Block block) {
        Optional<String> id = blocks.idAt(block);
        if (!player.isOnline() || id.isEmpty()) return; // already gone (double click, other plugin)
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;
        // onBreak (HIGHEST) has untracked the id, removed the projection and dropped the item by now.
        // A waterlogged slab or stair leaves its water behind, as in vanilla.
        boolean wet = block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        block.setType(wet ? Material.WATER : Material.AIR, false);
        mining.damageTool(player, id.get());
    }

    /**
     * Clicks that reach the interaction entity instead of the barrier. Paper sends {@link PlayerInteractAtEntityEvent}
     * (which carries the click position) and then the plain {@link PlayerInteractEntityEvent} for one click, and the
     * first is a subclass of the second: only the plain event fires the API event, the other just remembers the face.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectionInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction) || !blocks.isProjectionEntity(entity)) return;
        Player player = event.getPlayer();
        if (event instanceof PlayerInteractAtEntityEvent at) {
            var position = at.getClickedPosition();
            lastClickedFace.put(player.getUniqueId(),
                    ProjectionSpec.faceOfInteractionClick(position.getX(), position.getY(), position.getZ()));
            return;
        }
        Optional<Block> block = blocks.projectedBlock(entity);
        Optional<String> id = block.flatMap(blocks::idAt);
        if (id.isEmpty()) { blocks.removeStale(entity); return; } // block was removed behind our back
        event.setCancelled(true);
        if (!registry.blocks().containsKey(id.get())) return; // unknown plugin: placeholder, nothing to relay
        ProjectionSpec.Face face = lastClickedFace.remove(player.getUniqueId());
        ItemStack held = player.getInventory().getItem(event.getHand());
        boolean holding = held != null && !held.getType().isAir();
        Bukkit.getPluginManager().callEvent(new FlowTailBlockInteractEvent(player, block.get(), id.get(), event.getHand(),
                holding ? held : null, toBlockFace(face == null ? ProjectionSpec.Face.UP : face)));
    }

    private static BlockFace toBlockFace(ProjectionSpec.Face face) {
        return switch (face) {
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { explosion(event.blockList()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { explosion(event.blockList()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { if (anyTracked(event.getBlocks())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { if (anyTracked(event.getBlocks())) event.setCancelled(true); }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) { blocks.forget(event.getChunk()); }

    private boolean anyTracked(List<Block> list) {
        for (Block block : list) if (blocks.idAt(block).isPresent()) return true;
        return false;
    }

    /** Custom blocks leave the vanilla destroy list: ours decide breakable/drops; unknown ids always survive. */
    private void explosion(List<Block> list) {
        Iterator<Block> iterator = list.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<String> id = blocks.idAt(block);
            if (id.isEmpty()) continue;
            iterator.remove();
            JsonObject definition = registry.blocks().get(id.get());
            if (definition == null || !flag(definition, "explosion", "breakable")) continue;
            blocks.untrack(block);
            block.setType(org.bukkit.Material.AIR, false);
            if (flag(definition, "explosion", "drops")) dropSelf(block, id.get(), definition);
            plugin.getLogger().info("Custom block destroyed by explosion plugin_id=" + owner(id.get())
                    + " content_id=" + id.get() + " at=" + where(block));
        }
    }

    /** Break sound from the block's {@code sounds.preset} and item-crumb particles from its own model texture. */
    private void breakEffects(Block block, String id, JsonObject definition) {
        String preset = definition.has("sounds") && definition.get("sounds").isJsonObject()
                && definition.getAsJsonObject("sounds").has("preset")
                ? definition.getAsJsonObject("sounds").get("preset").getAsString() : null;
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        SoundPresets.breakSound(preset).ifPresent(key -> block.getWorld().playSound(at, key, SoundCategory.BLOCKS, 1.0f, 0.8f));
        if (prototypes.has(id)) block.getWorld().spawnParticle(Particle.ITEM, at, 24, 0.25, 0.25, 0.25, 0.05, prototypes.create(id));
    }

    private void dropSelf(Block block, String id, JsonObject definition) {
        String type = definition.has("drops") && definition.get("drops").isJsonObject()
                && definition.getAsJsonObject("drops").has("type")
                ? definition.getAsJsonObject("drops").get("type").getAsString() : "self";
        if (!type.equals("self")) {
            plugin.getLogger().warning("Block plugin_id=" + owner(id) + " content_id=" + id + " has drops.type=" + type
                    + " which is not supported until Phase 3; nothing dropped");
            return;
        }
        if (!prototypes.has(id)) { // item.auto=false: the block has a model but no giveable item to drop
            plugin.getLogger().warning("Block plugin_id=" + owner(id) + " content_id=" + id
                    + " has item.auto=false so there is no item to drop; nothing dropped");
            return;
        }
        ItemStack drop = prototypes.create(id);
        Location where = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(where, drop);
    }

    /** Reads definition.section.key as a boolean, true when absent (matching the compiler defaults). */
    private static boolean flag(JsonObject definition, String section, String key) {
        if (!definition.has(section) || !definition.get(section).isJsonObject()) return true;
        JsonObject object = definition.getAsJsonObject(section);
        return !object.has(key) || object.get(key).getAsBoolean();
    }

    private static String owner(String id) { return id.substring(0, id.indexOf(':')); }

    private static String where(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
