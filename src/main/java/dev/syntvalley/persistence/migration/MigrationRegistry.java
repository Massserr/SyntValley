package dev.syntvalley.persistence.migration;

import dev.syntvalley.persistence.codec.PersistenceException;
import dev.syntvalley.persistence.codec.UnsupportedSchemaVersionException;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Sequential, immutable migration registry. Schema 1 intentionally has no predecessor migrator. */
public final class MigrationRegistry {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final MigrationRegistry CURRENT = new MigrationRegistry(CURRENT_SCHEMA_VERSION, Map.of());

    private final int currentVersion;
    private final Map<Integer, TagMigrator> migrationsBySourceVersion;

    public MigrationRegistry(int currentVersion, Map<Integer, TagMigrator> migrationsBySourceVersion) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("Current schema version must be positive");
        }
        this.currentVersion = currentVersion;
        this.migrationsBySourceVersion = Map.copyOf(Objects.requireNonNull(migrationsBySourceVersion, "migrationsBySourceVersion"));
    }

    public static MigrationRegistry current() {
        return CURRENT;
    }

    public MigrationResult migrate(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        if (!source.contains("schema_version", Tag.TAG_INT)) {
            throw new PersistenceException("schema_version", "missing or wrong NBT type");
        }

        int foundVersion = source.getInt("schema_version");
        if (foundVersion > currentVersion) {
            throw new UnsupportedSchemaVersionException(foundVersion, currentVersion);
        }
        if (foundVersion < 1) {
            throw new PersistenceException("schema_version", "version must be positive");
        }

        CompoundTag migrated = source.copy();
        int version = foundVersion;
        while (version < currentVersion) {
            TagMigrator migrator = migrationsBySourceVersion.get(version);
            if (migrator == null) {
                throw new PersistenceException("schema_version", "missing migration " + version + " -> " + (version + 1));
            }
            migrated = Objects.requireNonNull(migrator.migrate(migrated.copy()), "migrator result");
            version++;
            migrated.putInt("schema_version", version);
        }

        return new MigrationResult(migrated, version != foundVersion);
    }
}
