package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentReportTest {
    @Test
    void listGroupsByPluginAndSorts() {
        List<String> lines = ContentReport.list("blocks", List.of("b:x", "a:z", "a:y"));
        assertEquals(List.of("3 blocks:", "a (2): a:y, a:z", "b (1): b:x"), lines);
        assertEquals(List.of("No blocks registered."), ContentReport.list("blocks", List.of()));
    }

    @Test
    void missingCountsOnlyUnknownIds() {
        List<String> lines = ContentReport.missing(List.of("a:x", "old:t", "old:t", "a:x"), Set.of("a:x"));
        assertEquals(List.of("old:t x2"), lines);
        assertTrue(ContentReport.missing(List.of("a:x"), Set.of("a:x")).isEmpty());
    }

    @Test
    void inspectDescribesAddOnlyProjection() {
        List<String> lines = ContentReport.inspect("a:x", true, "world:1,2,3", "minecraft:barrier",
                "display=1 interaction=1 state=v2:a:x (healthy)");
        assertTrue(lines.get(2).contains("minecraft:barrier"));
        assertTrue(lines.get(3).contains("display=1 interaction=1"));
        assertTrue(lines.get(4).contains("interaction entity"));
        assertTrue(ContentReport.inspect("a:x", false, "w:0,0,0", "minecraft:barrier", "display=0 interaction=0 state=none")
                .get(0).contains("UNKNOWN"));
    }
}
