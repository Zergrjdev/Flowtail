package dev.flowtail.content;

import java.util.concurrent.atomic.AtomicReference;

/** Shared registry state between Paper's bootstrapper and plugin instance. */
public final class ContentRuntime {
    private static final AtomicReference<ContentRegistry> REGISTRY = new AtomicReference<>();
    private static volatile java.nio.file.Path serverRoot;
    private static volatile PackGenerator.Result pack;
    private static volatile ContentSettings settings;
    private static volatile ItemPrototypes itemPrototypes;

    private ContentRuntime() {}

    static void install(ContentRegistry registry, java.nio.file.Path root,
                        PackGenerator.Result generatedPack, ContentSettings contentSettings) {
        if (!REGISTRY.compareAndSet(null, registry)) {
            throw new IllegalStateException("content registry was initialized more than once");
        }
        serverRoot = root;
        pack = generatedPack;
        settings = contentSettings;
    }

    public static ContentRegistry registry() {
        ContentRegistry registry = REGISTRY.get();
        if (registry == null) {
            throw new IllegalStateException("content registry was accessed before bootstrap");
        }
        return registry;
    }

    static java.nio.file.Path serverRoot() { return serverRoot; }
    static PackGenerator.Result pack() { return pack; }
    static ContentSettings settings() { return settings; }

    static void installItemPrototypes(ItemPrototypes prototypes) { itemPrototypes = prototypes; }
    public static ItemPrototypes itemPrototypes() {
        ItemPrototypes prototypes = itemPrototypes;
        if (prototypes == null) throw new IllegalStateException("item prototypes were accessed before plugin enable");
        return prototypes;
    }
}
