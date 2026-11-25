package dev.flowtail.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, validated view of the compiler's registry.json. */
public final class ContentRegistry {
    private static final Pattern PLUGIN_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final Path source;
    private final Map<String, JsonObject> plugins;
    private final Map<String, JsonObject> blocks;
    private final Map<String, JsonObject> items;
    private final Map<String, JsonObject> paintings;
    private final java.util.List<String> retiredBlocks;

    private ContentRegistry(Path source, Map<String, JsonObject> plugins, Map<String, JsonObject> blocks,
                            Map<String, JsonObject> items, Map<String, JsonObject> paintings,
                            java.util.List<String> retiredBlocks) {
        this.source = source;
        this.plugins = immutable(plugins);
        this.blocks = immutable(blocks);
        this.items = immutable(items);
        this.paintings = immutable(paintings);
        this.retiredBlocks = java.util.List.copyOf(retiredBlocks);
    }

    private static Map<String, JsonObject> immutable(Map<String, JsonObject> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static ContentRegistry load(Path path) {
        Path file = path.toAbsolutePath().normalize();
        final JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new ContentLoadException("file=" + file + " plugin_id=<registry> content_id=<registry>: "
                    + "could not read valid registry.json: " + e.getMessage(), e);
        }
        if (!root.has("schema") || root.get("schema").getAsInt() != 0) {
            throw invalid(file, "<registry>", "<registry>", "unsupported or missing registry schema");
        }

        Map<String, JsonObject> plugins = entries(file, root, "plugins", "id");
        for (String id : plugins.keySet()) {
            if (!PLUGIN_ID.matcher(id).matches()) {
                throw invalid(file, id, id, "invalid plugin id");
            }
        }
        Map<String, JsonObject> blocks = entries(file, root, "blocks", "id");
        Map<String, JsonObject> items = entries(file, root, "items", "id");
        Map<String, JsonObject> paintings = entries(file, root, "paintings", "id");
        validateContent(file, plugins.keySet(), "blocks", blocks, false);
        validateContent(file, plugins.keySet(), "items", items, false);
        validateContent(file, plugins.keySet(), "paintings", paintings, false);
        java.util.List<String> retired = new java.util.ArrayList<>();
        JsonElement retiredValue = root.get("retired_blocks");
        if (retiredValue != null && retiredValue.isJsonArray()) {
            for (JsonElement element : retiredValue.getAsJsonArray()) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) retired.add(element.getAsString());
            }
        }
        return new ContentRegistry(file, plugins, blocks, items, paintings, retired);
    }

    private static Map<String, JsonObject> entries(Path file, JsonObject root, String key, String idKey) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) {
            throw invalid(file, "<registry>", "<" + key + ">", "expected an array");
        }
        Map<String, JsonObject> result = new LinkedHashMap<>();
        JsonArray array = value.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            String id = "<entry-" + i + ">";
            try {
                JsonObject object = entry.getAsJsonObject();
                id = requiredString(object, idKey);
                boolean validId = key.equals("plugins")
                        ? PLUGIN_ID.matcher(id).matches()
                        : CONTENT_ID.matcher(id).matches();
                if (!validId) {
                    throw invalid(file, owner(id), id, "invalid namespaced content id");
                }
                if (result.putIfAbsent(id, object.deepCopy()) != null) {
                    throw invalid(file, owner(id), id, "duplicate id in " + key);
                }
            } catch (ContentLoadException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ContentLoadException("file=" + file + " plugin_id=" + owner(id) + " content_id=" + id
                        + ": invalid " + key + " entry: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private static void validateContent(Path file, Set<String> pluginIds, String kind,
                                        Map<String, JsonObject> values, boolean validateCarrier) {
        for (Map.Entry<String, JsonObject> value : values.entrySet()) {
            String id = value.getKey();
            String plugin = owner(id);
            JsonObject object = value.getValue();
            if (!pluginIds.contains(plugin)) {
                throw invalid(file, plugin, id, "content refers to a plugin not listed in registry.plugins");
            }
            if (kind.equals("paintings")) {
                int width = integer(file, plugin, id, object, "width");
                int height = integer(file, plugin, id, object, "height");
                if (width < 1 || width > 16 || height < 1 || height > 16) {
                    throw invalid(file, plugin, id, "painting width and height must each be between 1 and 16");
                }
                requiredString(file, plugin, id, object, "asset_id");
            }
            if (validateCarrier) {
                JsonObject carrier = object.has("carrier") && object.get("carrier").isJsonObject()
                        ? object.getAsJsonObject("carrier") : null;
                if (carrier == null || !carrier.has("block") || !carrier.has("state")
                        || !carrier.get("state").isJsonObject()) {
                    throw invalid(file, plugin, id, "block has no valid carrier block/state");
                }
            }
        }
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("missing/non-string field " + key);
        }
        return value.getAsString();
    }

    private static String requiredString(Path file, String plugin, String id, JsonObject object, String key) {
        try {
            return requiredString(object, key);
        } catch (RuntimeException e) {
            throw new ContentLoadException("file=" + file + " plugin_id=" + plugin + " content_id=" + id
                    + ": " + e.getMessage(), e);
        }
    }

    private static int integer(Path file, String plugin, String id, JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("missing/non-integer field " + key);
            }
            return value.getAsInt();
        } catch (RuntimeException e) {
            throw new ContentLoadException("file=" + file + " plugin_id=" + plugin + " content_id=" + id
                    + ": " + e.getMessage(), e);
        }
    }

    private static String owner(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? "<unknown>" : id.substring(0, separator);
    }

    private static ContentLoadException invalid(Path file, String plugin, String content, String reason) {
        return new ContentLoadException("file=" + file + " plugin_id=" + plugin + " content_id=" + content
                + ": " + reason);
    }

    public Path source() { return source; }
    public Map<String, JsonObject> plugins() { return plugins; }
    public Map<String, JsonObject> blocks() { return blocks; }
    public Map<String, JsonObject> items() { return items; }
    public Map<String, JsonObject> paintings() { return paintings; }
    /** Block ids that were registered before but whose plugin is no longer installed (their ids stay reserved). */
    public java.util.List<String> retiredBlocks() { return retiredBlocks; }
}
