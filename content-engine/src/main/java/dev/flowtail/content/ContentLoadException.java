package dev.flowtail.content;

/** A registry error whose message identifies the file and affected content. */
public final class ContentLoadException extends IllegalStateException {
    public ContentLoadException(String message) {
        super(message);
    }

    public ContentLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
