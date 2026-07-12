package dev.syntvalley.persistence.migration;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public record MigrationResult(CompoundTag root, boolean migrated) {
    public MigrationResult {
        Objects.requireNonNull(root, "root");
    }
}
