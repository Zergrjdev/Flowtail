package dev.flowtail.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small server-side reader for Phase 2 content settings from config/flowtail.properties. */
public record ContentSettings(boolean hostEnabled, int hostPort, String publicUrl,
                              boolean packRequired, String declineAction) {
    public static ContentSettings load(Path serverRoot) {
        Properties values = new Properties();
        Path path = serverRoot.resolve("config/flowtail.properties");
        if (Files.isRegularFile(path)) {
            try (var input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IOException error) {
                throw new IllegalStateException("Could not read " + path + ": " + error.getMessage(), error);
            }
        }
        boolean enabled = Boolean.parseBoolean(values.getProperty("pack.host.enabled", "true").trim());
        int port;
        try {
            port = Integer.parseInt(values.getProperty("pack.host.port", "8080").trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException("pack.host.port must be an integer in " + path, error);
        }
        if (port < 1 || port > 65535) throw new IllegalStateException("pack.host.port must be 1-65535 in " + path);
        String url = values.getProperty("pack.host.public-url", "auto").trim();
        boolean required = Boolean.parseBoolean(values.getProperty("pack.required", "true").trim());
        String decline = values.getProperty("pack.decline-action", "kick").trim();
        if (!decline.equals("kick") && !decline.equals("limbo-lag")) {
            throw new IllegalStateException("pack.decline-action must be kick or limbo-lag in " + path);
        }
        return new ContentSettings(enabled, port, url, required, decline);
    }
}
