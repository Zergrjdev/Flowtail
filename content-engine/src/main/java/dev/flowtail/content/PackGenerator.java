package dev.flowtail.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Java server-side port of compiler/ftc/pack.py. ZIP bytes may differ; entry bytes are golden-tested. */
public final class PackGenerator {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private PackGenerator() {}

    public record Result(Path zip, Path registry, Path allocations, String sha1, long size, int entries) {}

    public static Result generate(List<Path> pluginFiles, Path stateDir, Path outDir, int packMajor, int packMinor) {
        try {
            return generateChecked(pluginFiles, stateDir, outDir, packMajor, packMinor);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Pack generation failed: " + e.getMessage(), e);
        }
    }

    private static Result generateChecked(List<Path> pluginFiles, Path stateDir, Path outDir,
                                          int packMajor, int packMinor)
            throws IOException, NoSuchAlgorithmException {
        List<Plugin> plugins = new ArrayList<>();
        for (Path path : pluginFiles) plugins.add(readPlugin(path));
        plugins.sort(Comparator.comparing(p -> string(p.manifest.getAsJsonObject("plugin"), "id")));
        Set<String> pluginIds = new HashSet<>();
        for (Plugin plugin : plugins) {
            String id = string(plugin.manifest.getAsJsonObject("plugin"), "id");
            if (!pluginIds.add(id)) throw new IllegalArgumentException("duplicate plugin id " + id);
        }

        Files.createDirectories(stateDir);
        Files.createDirectories(outDir);
        Path allocationPath = stateDir.resolve("allocations.json");
        JsonObject allocations = Files.exists(allocationPath)
                ? readJson(allocationPath).getAsJsonObject() : initialAllocations();
        if (intValue(allocations, "schema") != 0) throw new IllegalArgumentException("unsupported allocations schema");
        JsonObject slots = new JsonObject();
        if (allocations.has("pools") && allocations.getAsJsonObject("pools").has("cube.note_block")) {
            slots = allocations.getAsJsonObject("pools").getAsJsonObject("cube.note_block").getAsJsonObject("slots");
        }
        JsonObject knownBlocks = allocations.has("blocks") ? allocations.getAsJsonObject("blocks") : new JsonObject();
        allocations.add("blocks", knownBlocks);
        for (Map.Entry<String, JsonElement> legacy : slots.entrySet()) {
            if (!knownBlocks.has(legacy.getKey())) {
                knownBlocks.addProperty(legacy.getKey(), !legacy.getValue().getAsJsonObject().get("retired").getAsBoolean());
            }
        }
        allocations.remove("pools"); // migrate legacy carrier slots to plain retained IDs

        JsonObject registry = new JsonObject();
        registry.addProperty("schema", 0);
        registry.addProperty("compiler", "0.1.0");
        JsonArray format = new JsonArray(); format.add(packMajor); format.add(packMinor);
        registry.add("pack_format", format);
        JsonArray pluginEntries = new JsonArray(), blockEntries = new JsonArray();
        JsonArray itemEntries = new JsonArray(), paintingEntries = new JsonArray();
        Set<String> liveBlocks = new HashSet<>();
        for (Plugin plugin : plugins) {
            JsonObject content = plugin.content;
            JsonObject owner = content.getAsJsonObject("plugin").deepCopy();
            owner.addProperty("has_code", content.get("has_code").getAsBoolean());
            owner.addProperty("has_datapack", content.get("has_datapack").getAsBoolean());
            pluginEntries.add(owner);
            append(itemEntries, content.getAsJsonArray("items"));
            append(paintingEntries, content.getAsJsonArray("paintings"));
            for (JsonElement element : content.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject().deepCopy();
                String id = string(block, "id");
                liveBlocks.add(id);
                knownBlocks.addProperty(id, true);
                if (slots.has(id)) slots.getAsJsonObject(id).addProperty("retired", false);
                blockEntries.add(block);
            }
        }
        List<String> retired = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : knownBlocks.entrySet()) {
            if (!liveBlocks.contains(entry.getKey())) knownBlocks.addProperty(entry.getKey(), false);
        }
        for (Map.Entry<String, JsonElement> entry : knownBlocks.entrySet()) {
            if (!entry.getValue().getAsBoolean()) retired.add(entry.getKey());
        }
        retired.sort(String::compareTo);
        JsonArray retiredArray = new JsonArray(); retired.forEach(retiredArray::add);
        registry.add("plugins", pluginEntries); registry.add("blocks", blockEntries);
        registry.add("items", itemEntries); registry.add("paintings", paintingEntries);
        registry.add("retired_blocks", retiredArray);

        Map<String, byte[]> merged = new TreeMap<>();
        Map<String, String> fileOwners = new HashMap<>();
        for (Plugin plugin : plugins) {
            String pluginId = string(plugin.manifest.getAsJsonObject("plugin"), "id");
            for (Map.Entry<String, byte[]> entry : plugin.files.entrySet()) {
                if (!entry.getKey().startsWith("pack/")) continue;
                String name = entry.getKey().substring("pack/".length());
                if (name.startsWith("assets/minecraft/blockstates/")
                        || name.startsWith("assets/minecraft/models/block/")
                        || name.startsWith("assets/minecraft/textures/block/")) {
                    throw new IllegalArgumentException("plugin " + pluginId + " may not override vanilla block assets: " + name);
                }
                byte[] previous = merged.get(name);
                if (previous == null) {
                    merged.put(name, entry.getValue()); fileOwners.put(name, pluginId);
                } else if (!MessageDigest.isEqual(previous, entry.getValue())) {
                    // Match compiler default: first plugin, sorted by id, wins.
                }
            }
        }
        // Custom blocks use item models on display entities. Never override vanilla block states or models.
        JsonObject pack = new JsonObject(); pack.addProperty("description", "FlowTail content pack");
        JsonArray min = new JsonArray(); min.add(packMajor); min.add(packMinor);
        JsonArray max = new JsonArray(); max.add(packMajor); max.add(packMinor);
        pack.add("min_format", min); pack.add("max_format", max);
        JsonObject meta = new JsonObject(); meta.add("pack", pack);
        merged.put("pack.mcmeta", jsonBytes(meta));

        Path zipPath = outDir.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.setLevel(9);
            for (Map.Entry<String, byte[]> entry : merged.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(315532800000L); // 1980-01-01, same epoch as the Python generator
                zipEntry.setMethod(ZipEntry.DEFLATED);
                zip.putNextEntry(zipEntry); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        byte[] zipBytes = Files.readAllBytes(zipPath);
        String sha1 = hex(MessageDigest.getInstance("SHA-1").digest(zipBytes));
        Files.writeString(outDir.resolve("pack.sha1"), sha1 + "\n", StandardCharsets.UTF_8);
        JsonObject info = new JsonObject(); info.addProperty("sha1", sha1); info.addProperty("size", zipBytes.length);
        info.add("pack_format", format); info.addProperty("entries", merged.size());
        Files.write(outDir.resolve("pack.json"), jsonBytes(info));
        Path registryPath = outDir.resolve("registry.json");
        Files.write(registryPath, jsonBytes(registry));
        Files.write(allocationPath, jsonBytes(allocations));
        return new Result(zipPath, registryPath, allocationPath, sha1, zipBytes.length, merged.size());
    }

    private record Plugin(JsonObject manifest, JsonObject content, Map<String, byte[]> files) {}

    private static Plugin readPlugin(Path path) throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) files.put(entry.getName(), zip.readAllBytes());
            }
        }
        JsonObject manifest = parse(files, "manifest.json").getAsJsonObject();
        for (Map.Entry<String, JsonElement> file : manifest.getAsJsonObject("files").entrySet()) {
            byte[] bytes = files.get(file.getKey());
            if (bytes == null || !hex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .equals(file.getValue().getAsString())) {
                throw new IllegalArgumentException("plugin " + path + " is damaged: " + file.getKey());
            }
        }
        return new Plugin(manifest, parse(files, "content.json").getAsJsonObject(), files);
    }

    private static JsonElement parse(Map<String, byte[]> files, String name) {
        byte[] bytes = files.get(name);
        if (bytes == null) throw new IllegalArgumentException("plugin archive is missing " + name);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
    }

    private static JsonElement readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static JsonObject initialAllocations() {
        JsonObject root = new JsonObject(); root.addProperty("schema", 0); root.add("blocks", new JsonObject());
        return root;
    }

    private static String modelFor(JsonObject registry, String id) {
        for (JsonElement block : registry.getAsJsonArray("blocks")) {
            if (string(block.getAsJsonObject(), "id").equals(id)) return string(block.getAsJsonObject(), "model");
        }
        throw new IllegalStateException("allocated live block is absent from registry: " + id);
    }

    private static void append(JsonArray destination, JsonArray source) {
        for (JsonElement element : source) destination.add(element.deepCopy());
    }


    private static byte[] jsonBytes(JsonElement value) {
        return (JSON.toJson(sort(value)) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonElement sort(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key, child) -> sorted.add(key, sort(child)));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray(); for (JsonElement child : value.getAsJsonArray()) array.add(sort(child)); return array;
        }
        return value.deepCopy();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("missing string field " + name);
        return value.getAsString();
    }

    private static int intValue(JsonObject object, String name) { return object.get(name).getAsInt(); }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte part : value) out.append(String.format("%02x", part & 0xff));
        return out.toString();
    }
}
