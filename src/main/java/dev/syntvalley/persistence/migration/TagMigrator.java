package dev.syntvalley.persistence.migration;

import net.minecraft.nbt.CompoundTag;

@FunctionalInterface
public interface TagMigrator {
    CompoundTag migrate(CompoundTag source);
}
