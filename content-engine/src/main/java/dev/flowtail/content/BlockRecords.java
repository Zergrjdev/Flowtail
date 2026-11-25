package dev.flowtail.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The custom blocks of one chunk: chunk-local position to namespaced content id (docs/13 invariant 1: saves store
 * ids, never carrier states). Pure logic with no Bukkit types.
 *
 * <p>Text format, one line per block: {@code x,y,z=namespace:id} with x and z in 0..15 and y the world height.
 * Lines this version cannot parse are kept verbatim and written back, so data from a newer or damaged save is never
 * silently dropped. Unknown content ids are ordinary records: removing a plugin must not delete its blocks.
 */
final class BlockRecords {
    private static final Pattern LINE = Pattern.compile(
            "(\\d{1,2}),(-?\\d{1,6}),(\\d{1,2})=([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\[([a-z0-9_=,]*)])?");

    private final TreeMap<Long, String> byPosition = new TreeMap<>();
    /** Display state of shaped blocks ({@code k=v,k=v}); a position without an entry has no state (cubes). */
    private final TreeMap<Long, String> states = new TreeMap<>();
    private final List<String> unparsed = new ArrayList<>();

    static long pack(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) throw new IllegalArgumentException("x and z must be 0..15");
        return ((long) y << 8) | ((long) x << 4) | z;
    }

    static BlockRecords decode(String text) {
        BlockRecords records = new BlockRecords();
        if (text == null || text.isBlank()) return records;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            Matcher match = LINE.matcher(line);
            if (!match.matches()) { records.unparsed.add(line); continue; }
            int x = Integer.parseInt(match.group(1)), z = Integer.parseInt(match.group(3));
            if (x > 15 || z > 15) { records.unparsed.add(line); continue; }
            long key = pack(x, Integer.parseInt(match.group(2)), z);
            if (records.byPosition.putIfAbsent(key, match.group(4)) != null) { records.unparsed.add(line); continue; }
            if (match.group(5) != null && !match.group(5).isEmpty()) records.states.put(key, match.group(5));
        }
        return records;
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Long, String> entry : byPosition.entrySet()) {
            long key = entry.getKey();
            out.append((key >> 4) & 15).append(',').append(key >> 8).append(',').append(key & 15)
                    .append('=').append(entry.getValue());
            String state = states.get(key);
            if (state != null) out.append('[').append(state).append(']');
            out.append('\n');
        }
        for (String line : unparsed) out.append(line).append('\n');
        return out.toString();
    }

    String get(int x, int y, int z) { return byPosition.get(pack(x, y, z)); }

    void put(int x, int y, int z, String id) { put(x, y, z, id, ""); }

    /** Records a block with its display state ({@code k=v,k=v}, empty for none). Replaces any earlier record here. */
    void put(int x, int y, int z, String id, String state) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid content id " + id);
        if (!state.matches("[a-z0-9_=,]*")) throw new IllegalArgumentException("invalid block state " + state);
        long key = pack(x, y, z);
        byPosition.put(key, id);
        if (state.isEmpty()) states.remove(key); else states.put(key, state);
    }

    /** The saved display state at a position, empty when none. */
    String state(int x, int y, int z) { return states.getOrDefault(pack(x, y, z), ""); }

    String remove(int x, int y, int z) {
        long key = pack(x, y, z);
        states.remove(key);
        return byPosition.remove(key);
    }

    /** Receives one record: chunk-local x and z (0..15), world y, content id. */
    interface Visitor { void at(int x, int y, int z, String id); }

    /** Visits every parsed record in position order. The visitor must not change this object. */
    void forEach(Visitor visitor) {
        for (Map.Entry<Long, String> entry : byPosition.entrySet()) {
            long key = entry.getKey();
            visitor.at((int) ((key >> 4) & 15), (int) (key >> 8), (int) (key & 15), entry.getValue());
        }
    }

    /** Receives one record with its state text. */
    interface StateVisitor { void at(int x, int y, int z, String id, String state); }

    /** Like {@link #forEach} but also gives the saved state. */
    void forEachWithState(StateVisitor visitor) {
        for (Map.Entry<Long, String> entry : byPosition.entrySet()) {
            long key = entry.getKey();
            visitor.at((int) ((key >> 4) & 15), (int) (key >> 8), (int) (key & 15), entry.getValue(), states.getOrDefault(key, ""));
        }
    }

    /** Every recorded content id (with repeats), in position order. */
    java.util.Collection<String> ids() { return Collections.unmodifiableCollection(byPosition.values()); }

    int size() { return byPosition.size(); }
    boolean isEmpty() { return byPosition.isEmpty() && unparsed.isEmpty(); }
    List<String> unparsed() { return Collections.unmodifiableList(unparsed); }
}
