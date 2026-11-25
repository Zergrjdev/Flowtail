package dev.flowtail.content;

import java.util.regex.Pattern;

/** Pure helpers for the FlowTail menu: colours, gradient, paging, access and label rules. No Bukkit/Adventure types. */
final class MenuStyle {
    static final int MAX_LABEL_CODE_POINTS = 32;
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");

    private MenuStyle() {}

    /** Parses {@code RRGGBB} (no leading '#': '#' starts a comment in flowtail.properties) into 0xRRGGBB. */
    static int parseHex(String key, String text) {
        if (text == null || !HEX.matcher(text).matches()) {
            throw new IllegalArgumentException(key + " must be 6 hex digits like 8A2BE2 (no #) but is '" + text + "'");
        }
        return Integer.parseInt(text, 16);
    }

    /** {@code count} colours from {@code from} to {@code to} in a straight line through RGB (count 1 gives {@code from}). */
    static int[] gradient(int from, int to, int count) {
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.0 : (double) i / (count - 1);
            int r = channel(from >> 16, to >> 16, t), g = channel(from >> 8, to >> 8, t), b = channel(from, to, t);
            out[i] = (r << 16) | (g << 8) | b;
        }
        return out;
    }

    private static int channel(int from, int to, double t) {
        int a = from & 0xFF, b = to & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }

    /** A button or title text: not blank, at most 32 characters, no control characters. */
    static String requireLabel(String key, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must not be empty");
        if (value.codePointCount(0, value.length()) > MAX_LABEL_CODE_POINTS) {
            throw new IllegalArgumentException(key + " must be at most " + MAX_LABEL_CODE_POINTS + " characters");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(key + " must not contain control characters");
        }
        return value;
    }

    /** Number of pages needed for {@code total} entries (always at least one so an empty menu still opens). */
    static int pageCount(int total, int perPage) {
        if (perPage < 1) throw new IllegalArgumentException("perPage must be at least 1");
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    static int clampPage(int page, int pageCount) { return Math.max(0, Math.min(page, pageCount - 1)); }

    /** access is creative, op or everyone (validated when settings load). */
    static boolean allowed(String access, boolean creative, boolean operator) {
        return switch (access) {
            case "creative" -> creative;
            case "op" -> operator;
            case "everyone" -> true;
            default -> throw new IllegalArgumentException("unknown menu access " + access);
        };
    }
}
