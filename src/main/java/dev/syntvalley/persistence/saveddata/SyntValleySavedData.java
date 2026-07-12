package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.observability.SyntValleyLog;
import dev.syntvalley.persistence.codec.PersistenceException;
import dev.syntvalley.persistence.codec.WorldStateCodec;
import dev.syntvalley.persistence.migration.MigrationRegistry;
import dev.syntvalley.persistence.migration.MigrationResult;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-scoped canonical SyntValley world state. */
public final class SyntValleySavedData extends SavedData {
    public static final String DATA_NAME = "syntvalley";
    public static final Factory<SyntValleySavedData> FACTORY = new Factory<>(
            SyntValleySavedData::createNew,
            (root, registries) -> loadFromTag(root)
    );

    private WorldState state;
    private final CompoundTag quarantinedRoot;
    private final String failureDiagnostic;

    private SyntValleySavedData(WorldState state, CompoundTag quarantinedRoot, String failureDiagnostic) {
        this.state = state;
        this.quarantinedRoot = quarantinedRoot;
        this.failureDiagnostic = failureDiagnostic;
    }

    public static SyntValleySavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static SyntValleySavedData loadFromTag(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        try {
            MigrationResult migration = MigrationRegistry.current().migrate(source);
            SyntValleySavedData loaded = new SyntValleySavedData(
                    WorldStateCodec.decode(migration.root()),
                    null,
                    null
            );
            if (migration.migrated()) {
                loaded.setDirty();
            }
            return loaded;
        } catch (RuntimeException exception) {
            String diagnostic = boundedDiagnostic(exception);
            SyntValleyLog.logger().error(
                    "SyntValley persistence is quarantined; gameplay mutations are disabled: {}",
                    diagnostic
            );
            return new SyntValleySavedData(null, source.copy(), diagnostic);
        }
    }

    private static SyntValleySavedData createNew() {
        return new SyntValleySavedData(WorldState.createNew(System.currentTimeMillis()), null, null);
    }

    public boolean isAvailable() {
        return state != null;
    }

    public Optional<String> failureDiagnostic() {
        return Optional.ofNullable(failureDiagnostic);
    }

    public WorldState stateSnapshot() {
        if (!isAvailable()) {
            throw new IllegalStateException("SyntValley persistence is quarantined");
        }
        return state;
    }

    void replaceState(WorldState replacement) {
        if (!isAvailable()) {
            throw new IllegalStateException("Cannot replace quarantined SyntValley state");
        }
        state = Objects.requireNonNull(replacement, "replacement");
    }

    public void updateLastFlushGameTime(long gameTime) {
        if (!isAvailable() || gameTime < 0 || state.lastFlushGameTime() == gameTime) {
            return;
        }
        state = state.withLastFlushGameTime(gameTime);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        if (!isAvailable()) {
            return quarantinedRoot.copy();
        }
        return WorldStateCodec.encodeInto(tag, state);
    }

    @Override
    public void setDirty() {
        if (isAvailable()) {
            super.setDirty();
        }
    }

    @Override
    public void setDirty(boolean dirty) {
        if (!dirty || isAvailable()) {
            super.setDirty(dirty);
        }
    }

    private static String boundedDiagnostic(RuntimeException exception) {
        String diagnostic = exception instanceof PersistenceException persistenceException
                ? persistenceException.diagnostic()
                : "unexpected " + exception.getClass().getSimpleName();
        if (diagnostic.length() <= PersistenceBounds.MAX_FAILURE_DIAGNOSTIC_CHARACTERS) {
            return diagnostic;
        }
        return diagnostic.substring(0, PersistenceBounds.MAX_FAILURE_DIAGNOSTIC_CHARACTERS);
    }
}
