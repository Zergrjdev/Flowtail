package dev.flowtail.content;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;

/** Creates canonical vanilla-backed stacks for FlowTail items and maps returned stacks back to ids. */
public final class ItemPrototypes {
    private static final NamespacedKey FLOWTAIL_ID = new NamespacedKey("flowtail", "id");

    private final ContentRegistry registry;
    private final Map<String, String> idByModel;
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ItemPrototypes(ContentRegistry registry) {
        this.registry = registry;
        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, JsonObject> entry : registry.items().entrySet()) {
            String model = string(entry.getValue(), "item_model");
            String previous = reverse.putIfAbsent(model, entry.getKey());
            if (previous != null) throw new IllegalArgumentException("duplicate item_model " + model
                    + " for " + previous + " and " + entry.getKey());
        }
        this.idByModel = Map.copyOf(reverse);
    }

    public ItemStack create(String id) { return create(id, 1); }

    public ItemStack create(String id, int count) {
        JsonObject prototype = registry.items().get(id);
        if (prototype == null) throw new IllegalArgumentException("Unknown FlowTail item " + id);
        Material base = baseMaterial(id, prototype);
        StackSizePolicy.Result stack = stackSize(id, prototype, base);
        if (count < 1 || count > stack.effective()) {
            throw new IllegalArgumentException("count must be between 1 and " + stack.effective() + " for " + id);
        }
        ItemStack created = new ItemStack(base);
        ItemMeta meta = created.getItemMeta();
        apply(meta, id, prototype, stack);
        created.setItemMeta(meta);
        created.setAmount(count);
        return created;
    }

    /** The maximum stack size a finished stack of this item has (damageable bases keep their own maximum of 1). */
    public int maxStackSize(String id) {
        JsonObject prototype = registry.items().get(id);
        if (prototype == null) throw new IllegalArgumentException("Unknown FlowTail item " + id);
        return stackSize(id, prototype, baseMaterial(id, prototype)).effective();
    }

    /**
     * Re-applies the prototype's own fields (item_model, item_name, max_stack_size, flowtail:id) on top of the stack
     * it was given. Everything else on the stack (enchantments, damage, lore, other plugins' data) is kept, because
     * creative clients send every moved stack back to the server. This method is the future packet-hook seam.
     */
    public ItemStack expand(ItemStack input) {
        Optional<String> id = idOf(input);
        if (id.isEmpty()) return input;
        JsonObject prototype = registry.items().get(id.get());
        if (prototype == null) return input;
        Material base = Material.matchMaterial(string(prototype, "base"));
        if (base == null || !base.isItem()) return input;
        ItemStack expanded = input.getType() == base ? input.clone() : new ItemStack(base, input.getAmount());
        ItemMeta meta = expanded.getItemMeta();
        apply(meta, id.get(), prototype, stackSize(id.get(), prototype, base));
        expanded.setItemMeta(meta);
        return expanded;
    }

    /** Reads the persistent id first, then the client-visible item_model as a reverse-map fallback. */
    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(FLOWTAIL_ID, PersistentDataType.STRING);
        if (stored != null && registry.items().containsKey(stored)) return Optional.of(stored);
        NamespacedKey model = meta.getItemModel();
        return model == null ? Optional.empty() : Optional.ofNullable(idByModel.get(model.toString()));
    }

    private static void apply(ItemMeta meta, String id, JsonObject prototype, StackSizePolicy.Result stack) {
        NamespacedKey itemModel = NamespacedKey.fromString(string(prototype, "item_model"));
        if (itemModel == null) throw new IllegalArgumentException("Invalid item_model for " + id);
        meta.setItemModel(itemModel);
        meta.itemName(Component.text(string(prototype, "name")));
        if (stack.overrideComponent()) {
            meta.setMaxStackSize(stack.effective());
        } else if (meta.hasMaxStackSize()) {
            meta.setMaxStackSize(null); // heals stacks created while a damageable base wrongly got a stack override
        }
        meta.getPersistentDataContainer().set(FLOWTAIL_ID, PersistentDataType.STRING, id);
    }

    private static Material baseMaterial(String id, JsonObject prototype) {
        String baseName = string(prototype, "base");
        Material base = Material.matchMaterial(baseName);
        if (base == null || !base.isItem()) throw new IllegalArgumentException("Invalid base item " + baseName + " for " + id);
        return base;
    }

    private StackSizePolicy.Result stackSize(String id, JsonObject prototype, Material base) {
        StackSizePolicy.Result result = StackSizePolicy.resolve(id, integer(prototype, "stack_size", base.getMaxStackSize()),
                base.getMaxStackSize(), base.getMaxDurability());
        if (result.warning() != null && warned.add(id)) {
            org.bukkit.Bukkit.getLogger().warning("[FlowTailContent] plugin_id=" + id.substring(0, id.indexOf(':'))
                    + " content_id=" + id + ": " + result.warning());
        }
        return result;
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("missing string field " + key);
        }
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isNumber()
                ? object.get(key).getAsInt() : fallback;
    }
}
