package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentRegistryTest {
    @TempDir Path directory;

    @Test
    void loadsExampleStyleRegistry() throws Exception {
        Path file = writeRegistry("""
                {"schema":0,"plugins":[{"id":"example"}],
                 "blocks":[{"id":"example:rock","carrier":{"block":"minecraft:note_block","state":{"instrument":"harp","note":"0","powered":"true"}}}],
                 "items":[{"id":"example:ruby"}],
                 "paintings":[{"id":"example:sunset","asset_id":"example:sunset","width":2,"height":1}]}
                """);

        ContentRegistry registry = ContentRegistry.load(file);

        assertEquals(1, registry.plugins().size());
        assertTrue(registry.blocks().containsKey("example:rock"));
        assertTrue(registry.items().containsKey("example:ruby"));
        assertEquals(2, registry.paintings().get("example:sunset").get("width").getAsInt());
    }

    @Test
    void invalidContentNamesPluginContentAndFile() throws Exception {
        Path file = writeRegistry("""
                {"schema":0,"plugins":[{"id":"example"}],"blocks":[],"items":[],
                 "paintings":[{"id":"example:oversize","asset_id":"example:oversize","width":17,"height":1}]}
                """);

        ContentLoadException error = assertThrows(ContentLoadException.class, () -> ContentRegistry.load(file));

        assertTrue(error.getMessage().contains("plugin_id=example"));
        assertTrue(error.getMessage().contains("content_id=example:oversize"));
        assertTrue(error.getMessage().contains(file.toAbsolutePath().toString()));
    }

    private Path writeRegistry(String contents) throws Exception {
        Path file = directory.resolve("registry.json");
        Files.writeString(file, contents);
        return file;
    }
}
