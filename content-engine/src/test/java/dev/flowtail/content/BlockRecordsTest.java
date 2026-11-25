package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockRecordsTest {
    @Test
    void roundTripIncludingNegativeHeightAndUnknownIds() {
        BlockRecords records = new BlockRecords();
        records.put(3, 64, 15, "example:rock");
        records.put(0, -60, 0, "removedplugin:gem_block");
        BlockRecords again = BlockRecords.decode(records.encode());
        assertEquals("example:rock", again.get(3, 64, 15));
        assertEquals("removedplugin:gem_block", again.get(0, -60, 0));
        assertEquals(2, again.size());
        assertEquals(records.encode(), again.encode());
    }

    @Test
    void unparsedAndDuplicateLinesArePreservedNotDropped() {
        BlockRecords records = BlockRecords.decode("1,5,1=a:b\ngarbage line\n1,5,1=a:c\n16,5,0=a:d\n");
        assertEquals("a:b", records.get(1, 5, 1));
        assertEquals(3, records.unparsed().size());
        BlockRecords again = BlockRecords.decode(records.encode());
        assertEquals(3, again.unparsed().size());
        assertTrue(again.encode().contains("garbage line"));
    }

    @Test
    void forEachVisitsEveryRecordWithItsOwnCoordinates() {
        BlockRecords records = new BlockRecords();
        records.put(3, 64, 15, "example:rock");
        records.put(0, -60, 0, "removedplugin:gem_block");
        java.util.List<String> seen = new java.util.ArrayList<>();
        records.forEach((x, y, z, id) -> seen.add(x + "," + y + "," + z + "=" + id));
        assertEquals(java.util.List.of("0,-60,0=removedplugin:gem_block", "3,64,15=example:rock"), seen);
    }

    @Test
    void removeAndEmpty() {
        BlockRecords records = new BlockRecords();
        records.put(1, 2, 3, "a:b");
        assertEquals("a:b", records.remove(1, 2, 3));
        assertNull(records.get(1, 2, 3));
        assertTrue(records.isEmpty());
        assertTrue(BlockRecords.decode(null).isEmpty());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new BlockRecords().put(16, 0, 0, "a:b"));
        assertThrows(IllegalArgumentException.class, () -> new BlockRecords().put(0, 0, 0, "NoNamespace"));
    }


    @Test
    void shapedBlocksKeepTheirDisplayState() {
        BlockRecords records = new BlockRecords();
        records.put(1, 70, 2, "shapes:weave_stairs", "facing=east,half=top,shape=outer_left");
        records.put(3, 70, 2, "example:bait_crate"); // a cube: no state
        BlockRecords again = BlockRecords.decode(records.encode());
        assertEquals("shapes:weave_stairs", again.get(1, 70, 2));
        assertEquals("facing=east,half=top,shape=outer_left", again.state(1, 70, 2));
        assertEquals("", again.state(3, 70, 2));
        assertEquals(records.encode(), again.encode());
        assertTrue(records.encode().contains("1,70,2=shapes:weave_stairs[facing=east,half=top,shape=outer_left]"));
        assertTrue(records.encode().contains("3,70,2=example:bait_crate\n"));
    }

    @Test
    void recordsWrittenBeforeShapesStillLoadAndReplacingClearsState() {
        BlockRecords old = BlockRecords.decode("3,64,15=example:rock\n");
        assertEquals("example:rock", old.get(3, 64, 15));
        assertEquals("", old.state(3, 64, 15));
        old.put(3, 64, 15, "a:b", "type=top");
        assertEquals("type=top", old.state(3, 64, 15));
        old.put(3, 64, 15, "a:b");
        assertEquals("", old.state(3, 64, 15), "put without a state clears it");
        old.put(3, 64, 15, "a:b", "type=top");
        assertEquals("a:b", old.remove(3, 64, 15));
        assertEquals("", old.state(3, 64, 15), "remove forgets the state too");
        assertThrows(IllegalArgumentException.class, () -> old.put(0, 0, 0, "a:b", "Type=Top"));
    }

    @Test
    void forEachWithStateVisitsStates() {
        BlockRecords records = new BlockRecords();
        records.put(0, 5, 0, "a:b", "type=top");
        java.util.List<String> seen = new java.util.ArrayList<>();
        records.forEachWithState((x, y, z, id, state) -> seen.add(id + "|" + state));
        assertEquals(java.util.List.of("a:b|type=top"), seen);
    }
}
