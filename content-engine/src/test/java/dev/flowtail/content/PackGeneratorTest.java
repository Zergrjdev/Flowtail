package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackGeneratorTest {
    @TempDir Path temp;

    @Test
    void examplePluginMatchesPythonGoldenVectors() throws Exception {
        Path golden = Path.of("../compiler/tests/golden").toAbsolutePath().normalize();
        Path stateDir = temp.resolve("state"), outputDir = temp.resolve("out");
        Files.createDirectories(stateDir);
        Files.copy(golden.resolve("allocations.json"), stateDir.resolve("allocations.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        PackGenerator.Result result = PackGenerator.generate(
                List.of(golden.resolve("example.ftplugin")), stateDir, outputDir, 97, 1);

        assertEquals(JsonParser.parseString(Files.readString(golden.resolve("registry.json"))),
                JsonParser.parseString(Files.readString(result.registry())));
        assertEquals(JsonParser.parseString(Files.readString(golden.resolve("allocations.json"))),
                JsonParser.parseString(Files.readString(result.allocations())));
        JsonObject expected = JsonParser.parseString(Files.readString(golden.resolve("pack-entry-sha256.json")))
                .getAsJsonObject();
        Map<String, String> actual = new TreeMap<>();
        try (InputStream in = Files.newInputStream(result.zip()); ZipInputStream zip = new ZipInputStream(in)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                actual.put(entry.getName(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(zip.readAllBytes())));
                zip.closeEntry(); entry = zip.getNextEntry();
            }
        }
        assertEquals(expected.size(), actual.size());
        for (Map.Entry<String, String> entry : actual.entrySet()) {
            assertEquals(expected.get(entry.getKey()).getAsString(), entry.getValue(), entry.getKey());
        }
        assertTrue(result.size() > 0);
    }
}
