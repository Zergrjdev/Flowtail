package dev.flowtail.content;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Builds the styled FlowTail label. Plain characters only, so a label can never inject formatting. */
final class MenuComponents {
    private MenuComponents() {}

    /** One colour per character, in a straight gradient from {@code from} to {@code to}, optionally bold. */
    static Component gradientLabel(String text, int from, int to, boolean bold) {
        int[] codePoints = text.codePoints().toArray();
        int[] colours = MenuStyle.gradient(from, to, codePoints.length);
        Component label = Component.empty();
        for (int i = 0; i < codePoints.length; i++) {
            label = label.append(Component.text(new String(Character.toChars(codePoints[i])), TextColor.color(colours[i])));
        }
        return bold ? label.decoration(TextDecoration.BOLD, true) : label;
    }

    static Component gradientLabel(String text, ContentSettings.MenuSettings menu) {
        return gradientLabel(text, menu.colorFrom(), menu.colorTo(), menu.bold());
    }
}
