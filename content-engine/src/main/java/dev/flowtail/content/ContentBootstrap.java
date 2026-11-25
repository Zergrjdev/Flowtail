package dev.flowtail.content;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.event.RegistryEvents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;

/** Loads the merged content registry before data-driven registries freeze. */
public final class ContentBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        Path source = context.getPluginSource().toAbsolutePath().normalize();
        Path serverRoot = source.getParent() == null ? Path.of(".").toAbsolutePath() : source.getParent().getParent();
        if (serverRoot == null) {
            serverRoot = Path.of(".").toAbsolutePath();
        }
        Path stateDir = serverRoot.resolve("content-state");
        Path registryPath = stateDir.resolve("registry.json");
        final ContentRegistry registry;
        final PackGenerator.Result pack;
        final ContentSettings settings;
        try {
            Path plugins = stateDir.resolve("plugins");
            List<Path> pluginFiles = List.of();
            if (Files.isDirectory(plugins)) {
                try (Stream<Path> paths = Files.list(plugins)) {
                    pluginFiles = paths.filter(p -> p.getFileName().toString().endsWith(".ftplugin")).sorted().toList();
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Could not list plugin archive directory " + plugins, error);
                }
            }
            int[] packFormat = pluginFiles.isEmpty() ? null : readFormat(registryPath);
            pack = pluginFiles.isEmpty() ? null : PackGenerator.generate(pluginFiles, stateDir,
                    stateDir.resolve("pack"), packFormat[0], packFormat[1]);
            settings = ContentSettings.load(serverRoot);
            registry = ContentRegistry.load(registryPath);
            ContentRuntime.install(registry, serverRoot, pack, settings);
        } catch (RuntimeException error) {
            context.getLogger().error("Content bootstrap failed: " + error.getMessage(), error);
            throw error;
        }

        context.getLogger().info("Loaded content registry file=" + registry.source()
                + " plugins=" + registry.plugins().size() + " blocks=" + registry.blocks().size()
                + " items=" + registry.items().size() + " paintings=" + registry.paintings().size());

        if (settings.menu().enabled()) MenuDialogs.register(context, settings.menu());

        context.getLifecycleManager().registerEventHandler(RegistryEvents.PAINTING_VARIANT.compose(), event -> {
            for (var entry : registry.paintings().entrySet()) {
                String id = entry.getKey();
                String plugin = id.substring(0, id.indexOf(':'));
                var data = entry.getValue();
                try {
                    var key = TypedKey.create(RegistryKey.PAINTING_VARIANT, Key.key(id));
                    event.registry().register(key, builder -> {
                        builder.width(data.get("width").getAsInt())
                                .height(data.get("height").getAsInt())
                                .assetId(Key.key(data.get("asset_id").getAsString()));
                        if (data.has("name")) {
                            builder.title(Component.text(data.get("name").getAsString()));
                        }
                        if (data.has("author")) {
                            builder.author(Component.text(data.get("author").getAsString()));
                        }
                    });
                    context.getLogger().info("Registered painting plugin_id=" + plugin + " content_id=" + id);
                } catch (RuntimeException error) {
                    String message = "Painting registration failed plugin_id=" + plugin + " content_id=" + id
                            + " file=" + registry.source() + ": " + error.getMessage();
                    context.getLogger().error(message, error);
                    throw new IllegalStateException(message, error);
                }
            }
        });
    }

    private static int[] readFormat(Path registryPath) {
        try {
            var format = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(registryPath))
                    .getAsJsonObject().getAsJsonArray("pack_format");
            if (format.size() != 2) throw new IllegalStateException("pack_format must be [major, minor] in " + registryPath);
            return new int[]{format.get(0).getAsInt(), format.get(1).getAsInt()};
        } catch (java.io.IOException | RuntimeException error) {
            throw new IllegalStateException("Could not read pack format from " + registryPath + ": " + error.getMessage(), error);
        }
    }
}
