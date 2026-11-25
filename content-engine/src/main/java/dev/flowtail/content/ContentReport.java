package dev.flowtail.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Text for the /content debug commands (P2-10). Pure logic, no Bukkit types. */
final class ContentReport {
    private ContentReport() {}

    /** Ids grouped by plugin: {@code example (2): example:ruby, example:rock}. */
    static List<String> list(String kind, Collection<String> ids) {
        List<String> out = new ArrayList<>();
        if (ids.isEmpty()) { out.add("No " + kind + " registered."); return out; }
        Map<String, Set<String>> byPlugin = new TreeMap<>();
        for (String id : ids) byPlugin.computeIfAbsent(id.substring(0, id.indexOf(':')), k -> new TreeSet<>()).add(id);
        out.add(ids.size() + " " + kind + ":");
        for (Map.Entry<String, Set<String>> entry : byPlugin.entrySet()) {
            out.add(entry.getKey() + " (" + entry.getValue().size() + "): " + String.join(", ", entry.getValue()));
        }
        return out;
    }

    /** Unknown ids with how many blocks use each: {@code old:thing x12}. Empty when everything is known. */
    static List<String> missing(Collection<String> recordedIds, Set<String> knownIds) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String id : recordedIds) if (!knownIds.contains(id)) counts.merge(id, 1, Integer::sum);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) out.add(entry.getKey() + " x" + entry.getValue());
        return out;
    }

    /** Lines describing one tracked block; {@code known=false} means its plugin is not installed. */
    static List<String> inspect(String id, boolean known, String position, String hitbox, String projection) {
        List<String> out = new ArrayList<>();
        out.add("Block " + id + (known ? "" : " (UNKNOWN: its plugin is not installed; the block is preserved)"));
        out.add("Plugin: " + id.substring(0, id.indexOf(':')) + "  At: " + position);
        out.add("Hitbox: " + hitbox + "  (saved id record: present)");
        out.add("Projection: " + projection);
        out.add("Visual: item display using item_model; click target: interaction entity");
        return out;
    }
}
