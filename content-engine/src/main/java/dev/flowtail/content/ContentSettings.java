package dev.flowtail.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small server-side reader for Phase 2 content settings from config/flowtail.properties. */
public record ContentSettings(boolean hostEnabled, int hostPort, String publicUrl,
                              boolean packRequired, String declineAction,
                              MenuSettings menu) {
    /** The FlowTail pause-screen button and the Custom Items chest menu (all keys start with {@code menu.}). */
    public record MenuSettings(boolean enabled, String label, int colorFrom, int colorTo, boolean bold,
                               String itemsLabel, String itemsTitle, String itemsAccess) {}

    public static ContentSettings load(Path serverRoot) {
        Properties values = new Properties();
        Path path = serverRoot.resolve("config/flowtail.properties");
        if (Files.isRegularFile(path)) {
            try (var input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IOException error) {
                throw new IllegalStateException("Could not read " + path + ": " + error.getMessage(), error);
            }
        }
        boolean enabled = Boolean.parseBoolean(get(values, "pack.host.enabled", "true"));
        int port;
        try {
            port = Integer.parseInt(get(values, "pack.host.port", "8080"));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("pack.host.port must be an integer in " + path, error);
        }
        if (port < 1 || port > 65535) throw new IllegalStateException("pack.host.port must be 1-65535 in " + path);
        String url = get(values, "pack.host.public-url", "auto");
        boolean required = Boolean.parseBoolean(get(values, "pack.required", "true"));
        String decline = get(values, "pack.decline-action", "kick");
        if (!decline.equals("kick") && !decline.equals("limbo-lag")) {
            throw new IllegalStateException("pack.decline-action must be kick or limbo-lag in " + path);
        }
        return new ContentSettings(enabled, port, url, required, decline, loadMenu(values, path));
    }

    private static MenuSettings loadMenu(Properties values, Path path) {
        try {
            String access = get(values, "menu.custom-items.access", "creative");
            if (!access.equals("creative") && !access.equals("op") && !access.equals("everyone")) {
                throw new IllegalArgumentException("menu.custom-items.access must be creative, op or everyone");
            }
            return new MenuSettings(
                    bool(values, "menu.enabled", "true"),
                    MenuStyle.requireLabel("menu.label", get(values, "menu.label", "FlowTail")),
                    MenuStyle.parseHex("menu.color.from", get(values, "menu.color.from", "8A2BE2")),
                    MenuStyle.parseHex("menu.color.to", get(values, "menu.color.to", "E040FB")),
                    bool(values, "menu.bold", "true"),
                    MenuStyle.requireLabel("menu.custom-items.label", get(values, "menu.custom-items.label", "Custom Items")),
                    MenuStyle.requireLabel("menu.custom-items.title", get(values, "menu.custom-items.title", "Custom Items")),
                    access);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(error.getMessage() + " in " + path, error);
        }
    }

    /** Same rules as parity/flowtail.py: a '#' starts a comment, surrounding space is ignored. */
    private static String get(Properties values, String key, String fallback) {
        return values.getProperty(key, fallback).split("#", 2)[0].trim();
    }

    private static boolean bool(Properties values, String key, String fallback) {
        String value = get(values, key, fallback).toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false but is '" + value + "'");
        };
    }
}
