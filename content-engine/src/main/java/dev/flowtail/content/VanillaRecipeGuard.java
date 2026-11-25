package dev.flowtail.content;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Keeps FlowTail items out of vanilla recipes of their base item.
 *
 * <p>A custom item is physically a vanilla base item (Ruby is paper), so without this guard Ruby + gunpowder crafts a
 * firework rocket and a paper/iron-sword based item can be smelted or used as fuel. Recipes registered by FlowTail
 * itself (namespace {@value #OWN_RECIPE_NAMESPACE}, arriving with P3-08) are exempt.
 */
final class VanillaRecipeGuard implements Listener {
    static final String OWN_RECIPE_NAMESPACE = "flowtail";

    private final FlowTailContentPlugin plugin;
    private final ItemPrototypes prototypes;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    VanillaRecipeGuard(FlowTailContentPlugin plugin, ItemPrototypes prototypes) {
        this.plugin = plugin; this.prototypes = prototypes;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null || isFlowTailRecipe(recipe)) return;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            Optional<String> id = prototypes.idOf(ingredient);
            if (id.isPresent()) {
                event.getInventory().setResult(null);
                note("crafting", id.get(), recipeName(recipe));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        Optional<String> id = prototypes.idOf(event.getSource());
        if (id.isPresent()) {
            event.setCancelled(true);
            note("smelting", id.get(), recipeName(event.getRecipe()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        Optional<String> id = prototypes.idOf(event.getFuel());
        if (id.isPresent()) {
            event.setCancelled(true);
            note("fuel", id.get(), "furnace fuel");
        }
    }

    static boolean isFlowTailRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && OWN_RECIPE_NAMESPACE.equals(keyed.getKey().getNamespace());
    }

    private static String recipeName(Recipe recipe) {
        if (recipe == null) return "unknown";
        return recipe instanceof Keyed keyed ? keyed.getKey().toString() : recipe.getClass().getSimpleName();
    }

    /** One info line per (use, item, recipe) so a player fiddling with a crafting grid does not flood the log. */
    private void note(String use, String id, String recipe) {
        if (logged.size() > 1000) logged.clear();
        if (logged.add(use + "|" + id + "|" + recipe)) {
            plugin.getLogger().info("Blocked vanilla " + use + " of custom item plugin_id="
                    + id.substring(0, id.indexOf(':')) + " content_id=" + id + " recipe=" + recipe);
        }
    }
}
