package dev.syntvalley.persistence.codec;

import java.util.Objects;

public class PersistenceException extends RuntimeException {
    private final String path;
    private final String reason;

    public PersistenceException(String path, String reason) {
        super(Objects.requireNonNull(path, "path") + ": " + Objects.requireNonNull(reason, "reason"));
        this.path = path;
        this.reason = reason;
    }

    public String path() {
        return path;
    }

    public String reason() {
        return reason;
    }

    public String diagnostic() {
        return path + ": " + reason;
    }
}
