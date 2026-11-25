package dev.flowtail.content;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Paper plugin entry point for runtime content features. */
public final class FlowTailContentPlugin extends JavaPlugin {
    private PackHost packHost;

    @Override
    public void onEnable() {
        ContentRegistry registry = ContentRuntime.registry();
        ItemPrototypes itemPrototypes = new ItemPrototypes(registry);
        ContentRuntime.installItemPrototypes(itemPrototypes);
        getServer().getPluginManager().registerEvents(new CreativeItemListener(this, itemPrototypes), this);
        getServer().getPluginManager().registerEvents(new ItemCommandListener(this, itemPrototypes, registry), this);
        verifyItemPrototypes(itemPrototypes, registry);
        getServer().getPluginManager().registerEvents(new VanillaRecipeGuard(this, itemPrototypes), this);
        ContentSettings blockSettings = ContentRuntime.settings();
        BlockManager blockManager = new BlockManager(this, registry, itemPrototypes);
        ContentRuntime.installBlocks(blockManager);
        getServer().getPluginManager().registerEvents(new ContentCommand(this, registry, itemPrototypes, blockManager), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this, registry, itemPrototypes, blockManager), this);
        getServer().getPluginManager().registerEvents(new BlockPickListener(this, registry, itemPrototypes, blockManager), this);
        if (blockSettings.menu().enabled()) {
            getServer().getPluginManager().registerEvents(new CustomItemsMenu(this, itemPrototypes, blockSettings.menu()), this);
            getLogger().info("Menu ready: label=" + blockSettings.menu().label()
                    + " custom_items_access=" + blockSettings.menu().itemsAccess() + " items=" + itemPrototypes.ids().size());
        }
        getLogger().info("Block engine ready: projection=barrier+item-display+interaction; vanilla_note_blocks=enabled");
        getLogger().info("Content engine ready: " + registry.blocks().size() + " blocks, "
                + registry.items().size() + " items, " + registry.paintings().size() + " paintings");
        PackGenerator.Result pack = ContentRuntime.pack();
        ContentSettings settings = ContentRuntime.settings();
        if (pack != null && settings.hostEnabled()) {
            try {
                packHost = new PackHost(settings, pack);
                getServer().getPluginManager().registerEvents(new PackDelivery(this, packHost, settings), this);
                getLogger().info("Resource pack host ready URL=" + packHost.url() + " sha1=" + pack.sha1()
                        + " required=" + settings.packRequired() + " decline_action=" + settings.declineAction());
            } catch (java.io.IOException | RuntimeException error) {
                getLogger().severe("Resource pack host failed: " + error.getMessage());
                throw new IllegalStateException(error);
            }
        } else if (settings.packRequired()) {
            getLogger().severe("pack.required=true but no generated content pack is available");
            throw new IllegalStateException("required resource pack is missing");
        }
    }

    @Override
    public void onDisable() {
        getServer().clearCustomBlockShapes(this);
        if (packHost != null) packHost.close();
    }

    private void verifyItemPrototypes(ItemPrototypes prototypes, ContentRegistry registry) {
        for (String id : prototypes.ids()) {
            ItemStack created = prototypes.create(id);
            if (!prototypes.idOf(created).filter(id::equals).isPresent()) {
                throw new IllegalStateException("Item prototype failed PDC reverse mapping plugin_id="
                        + id.substring(0, id.indexOf(':')) + " content_id=" + id);
            }
            ItemStack returned = ItemStack.deserialize(created.serialize());
            if (!prototypes.idOf(returned).filter(id::equals).isPresent()) {
                throw new IllegalStateException("Item prototype lost identity in ItemStack serialization plugin_id="
                        + id.substring(0, id.indexOf(':')) + " content_id=" + id);
            }
            var meta = returned.getItemMeta();
            meta.getPersistentDataContainer().remove(new NamespacedKey("flowtail", "id"));
            returned.setItemMeta(meta);
            if (!prototypes.idOf(returned).filter(id::equals).isPresent()) {
                throw new IllegalStateException("Item prototype failed item_model reverse mapping plugin_id="
                        + id.substring(0, id.indexOf(':')) + " content_id=" + id);
            }
            verifyStackSizeAndOverlay(prototypes, id);
            getLogger().info("Verified item prototype plugin_id=" + id.substring(0, id.indexOf(':'))
                    + " content_id=" + id + " item_model=" + prototypes.itemModel(id));
        }
    }

    /** Boot-time checks that need a real server: stack size rule and "expand keeps foreign data". */
    private void verifyStackSizeAndOverlay(ItemPrototypes prototypes, String id) {
        String plugin = id.substring(0, id.indexOf(':'));
        ItemStack created = prototypes.create(id);
        if (created.getMaxStackSize() != prototypes.maxStackSize(id)) {
            throw new IllegalStateException("Item prototype has wrong max stack size plugin_id=" + plugin
                    + " content_id=" + id + " expected=" + prototypes.maxStackSize(id)
                    + " actual=" + created.getMaxStackSize());
        }
        NamespacedKey foreign = new NamespacedKey("flowtail_selfcheck", "marker");
        var meta = created.getItemMeta();
        meta.lore(List.of(Component.text("flowtail-selfcheck")));
        meta.getPersistentDataContainer().set(foreign, PersistentDataType.STRING, "kept");
        created.setItemMeta(meta);
        var expanded = prototypes.expand(created).getItemMeta();
        if (!expanded.hasLore() || !"kept".equals(expanded.getPersistentDataContainer().get(foreign, PersistentDataType.STRING))) {
            throw new IllegalStateException("expand() dropped data it does not own plugin_id=" + plugin
                    + " content_id=" + id);
        }
    }
}
