package dev.syntvalley.persistence.codec;

public final class UnsupportedSchemaVersionException extends PersistenceException {
    private final int found;
    private final int supported;

    public UnsupportedSchemaVersionException(int found, int supported) {
        super("schema_version", "unsupported future schema " + found + "; supported " + supported);
        this.found = found;
        this.supported = supported;
    }

    public int found() {
        return found;
    }

    public int supported() {
        return supported;
    }
}
