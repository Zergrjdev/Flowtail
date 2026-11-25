package dev.flowtail.content;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Registers the FlowTail pause-screen button (bootstrap time, before the dialog registry freezes).
 *
 * <p>Minecraft 1.21.6+ shows every dialog in the {@code minecraft:pause_screen_additions} tag as a button on the pause
 * screen, labelled with the dialog's external title. The dialog holds one button, Custom Items, which sends a custom
 * click that {@link CustomItemsMenu} answers by opening the chest menu. Dialogs are data-driven and fixed at startup,
 * so every {@code menu.*} setting needs a restart.
 */
final class MenuDialogs {
    static final Key DIALOG_KEY = Key.key("flowtail:menu");
    static final Key CUSTOM_ITEMS_CLICK = Key.key("flowtail:menu/custom_items");

    private MenuDialogs() {}

    static void register(BootstrapContext context, ContentSettings.MenuSettings menu) {
        TypedKey<Dialog> dialogKey = DialogKeys.create(DIALOG_KEY);
        Component label = MenuComponents.gradientLabel(menu.label(), menu);
        String tooltip = switch (menu.itemsAccess()) {
            case "creative" -> "Creative mode only";
            case "op" -> "Operators only";
            default -> "Open the custom items menu";
        };
        ActionButton customItems = ActionButton.create(Component.text(menu.itemsLabel()), Component.text(tooltip), 200,
                DialogAction.customClick(CUSTOM_ITEMS_CLICK, null));
        ActionButton close = ActionButton.create(Component.text("Close"), null, 200, null);

        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
                event.registry().register(dialogKey, builder -> builder
                        .base(DialogBase.builder(label).externalTitle(label).canCloseWithEscape(true).build())
                        .type(DialogType.multiAction(List.of(customItems), close, 1))));

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event ->
                event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(dialogKey)));

        context.getLogger().info("Registered pause-screen menu dialog key=" + DIALOG_KEY.asString()
                + " label=" + menu.label() + " custom_items_access=" + menu.itemsAccess());
    }
}
