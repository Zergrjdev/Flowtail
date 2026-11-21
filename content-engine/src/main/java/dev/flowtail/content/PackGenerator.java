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
    private static final String POOL = "cube.note_block";
    private static final String CARRIER = "minecraft:note_block";
    private static final String DEFAULT = "instrument=harp,note=0,powered=false";
    private static final Map<String, List<String>> NOTE_PROPERTIES = noteProperties();

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
        JsonObject pool = allocations.getAsJsonObject("pools").getAsJsonObject(POOL);
        JsonObject slots = pool.getAsJsonObject("slots");
        List<Map<String, String>> available = poolSlots();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : slots.entrySet()) {
            used.add(stateKey(entry.getValue().getAsJsonObject().getAsJsonObject("state")));
        }
        available.removeIf(state -> used.contains(stateKey(state)));

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
                if (!slots.has(id)) {
                    if (available.isEmpty()) throw new IllegalStateException("no free carrier states remain for " + id);
                    JsonObject slot = new JsonObject();
                    slot.add("state", toJsonObject(available.removeFirst()));
                    slot.addProperty("retired", false);
                    slots.add(id, slot);
                } else {
                    slots.getAsJsonObject(id).addProperty("retired", false);
                }
                JsonObject carrier = new JsonObject();
                carrier.addProperty("pool", POOL); carrier.addProperty("block", CARRIER);
                carrier.add("state", slots.getAsJsonObject(id).getAsJsonObject("state").deepCopy());
                block.add("carrier", carrier); blockEntries.add(block);
            }
        }
        List<String> retired = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : slots.entrySet()) {
            if (!liveBlocks.contains(entry.getKey())) {
                entry.getValue().getAsJsonObject().addProperty("retired", true);
                retired.add(entry.getKey());
            }
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
                byte[] previous = merged.get(name);
                if (previous == null) {
                    merged.put(name, entry.getValue()); fileOwners.put(name, pluginId);
                } else if (!MessageDigest.isEqual(previous, entry.getValue())) {
                    // Match compiler default: first plugin, sorted by id, wins.
                }
            }
        }
        Map<String, String> byState = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : slots.entrySet()) {
            byState.put(stateKey(entry.getValue().getAsJsonObject().getAsJsonObject("state")), entry.getKey());
        }
        JsonObject variants = new JsonObject();
        for (Map<String, String> state : allStates()) {
            String key = stateKey(state), blockId = byState.get(key);
            String model = blockId == null ? "minecraft:block/note_block"
                    : liveBlocks.contains(blockId) ? modelFor(registry, blockId) : "minecraft:block/barrier";
            JsonObject modelObject = new JsonObject(); modelObject.addProperty("model", model);
            variants.add(key, modelObject);
        }
        JsonObject blockstate = new JsonObject(); blockstate.add("variants", variants);
        merged.put("assets/minecraft/blockstates/note_block.json", jsonBytes(blockstate));
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
        JsonObject slots = new JsonObject(); JsonObject pool = new JsonObject();
        pool.addProperty("block", CARRIER); pool.add("slots", slots);
        JsonObject pools = new JsonObject(); pools.add(POOL, pool);
        JsonObject root = new JsonObject(); root.addProperty("schema", 0); root.add("pools", pools);
        return root;
    }

    private static List<Map<String, String>> poolSlots() {
        List<Map<String, String>> states = allStates();
        states.removeIf(state -> stateKey(state).equals(DEFAULT));
        return states;
    }

    private static List<Map<String, String>> allStates() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String instrument : NOTE_PROPERTIES.get("instrument"))
            for (String note : NOTE_PROPERTIES.get("note"))
                for (String powered : NOTE_PROPERTIES.get("powered")) {
                    Map<String, String> state = new TreeMap<>();
                    state.put("instrument", instrument); state.put("note", note); state.put("powered", powered);
                    result.add(state);
                }
        return result;
    }

    private static Map<String, List<String>> noteProperties() {
        Map<String, List<String>> props = new LinkedHashMap<>();
        props.put("instrument", List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
                "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling",
                "trumpet", "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered", "zombie", "skeleton",
                "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"));
        List<String> notes = new ArrayList<>(); for (int i = 0; i < 25; i++) notes.add(Integer.toString(i));
        props.put("note", notes); props.put("powered", List.of("true", "false"));
        return Map.copyOf(props);
    }

    private static String stateKey(Map<String, String> state) {
        return String.join(",", new TreeMap<>(state).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
    }

    private static String stateKey(JsonObject state) {
        Map<String, String> values = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : state.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
        return stateKey(values);
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

    private static JsonObject toJsonObject(Map<String, String> values) {
        JsonObject object = new JsonObject(); new TreeMap<>(values).forEach(object::addProperty); return object;
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
