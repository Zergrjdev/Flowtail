package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuStyleTest {
    @Test
    void hexParsingAcceptsSixDigitsOnly() {
        assertEquals(0x8A2BE2, MenuStyle.parseHex("k", "8A2BE2"));
        assertEquals(0xe040fb, MenuStyle.parseHex("k", "e040fb"));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.parseHex("k", "#8A2BE2"));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.parseHex("k", "8A2BE"));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.parseHex("k", "GGGGGG"));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.parseHex("k", null));
    }

    @Test
    void gradientEndpointsAndMiddle() {
        assertArrayEquals(new int[0], MenuStyle.gradient(0, 0xFFFFFF, 0));
        assertArrayEquals(new int[]{0x8A2BE2}, MenuStyle.gradient(0x8A2BE2, 0xE040FB, 1));
        assertArrayEquals(new int[]{0x000000, 0xFFFFFF}, MenuStyle.gradient(0x000000, 0xFFFFFF, 2));
        assertArrayEquals(new int[]{0x000000, 0x808080, 0xFFFFFF}, MenuStyle.gradient(0x000000, 0xFFFFFF, 3));
        int[] flowtail = MenuStyle.gradient(0x8A2BE2, 0xE040FB, 8);
        assertEquals(8, flowtail.length);
        assertEquals(0x8A2BE2, flowtail[0]);
        assertEquals(0xE040FB, flowtail[7]);
    }

    @Test
    void gradientWorksWhenColoursDecrease() {
        assertArrayEquals(new int[]{0xFF0000, 0x800000, 0x000000}, MenuStyle.gradient(0xFF0000, 0x000000, 3));
    }

    @Test
    void labelRules() {
        assertEquals("FlowTail", MenuStyle.requireLabel("k", "FlowTail"));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.requireLabel("k", "  "));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.requireLabel("k", "x".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.requireLabel("k", "a\nb"));
        assertEquals("x".repeat(32), MenuStyle.requireLabel("k", "x".repeat(32)));
    }

    @Test
    void pagingMath() {
        assertEquals(1, MenuStyle.pageCount(0, 45));
        assertEquals(1, MenuStyle.pageCount(45, 45));
        assertEquals(2, MenuStyle.pageCount(46, 45));
        assertEquals(0, MenuStyle.clampPage(-3, 2));
        assertEquals(1, MenuStyle.clampPage(9, 2));
    }

    @Test
    void accessRules() {
        assertTrue(MenuStyle.allowed("creative", true, false));
        assertFalse(MenuStyle.allowed("creative", false, true));
        assertTrue(MenuStyle.allowed("op", false, true));
        assertFalse(MenuStyle.allowed("op", true, false));
        assertTrue(MenuStyle.allowed("everyone", false, false));
        assertThrows(IllegalArgumentException.class, () -> MenuStyle.allowed("nobody", true, true));
    }
}
