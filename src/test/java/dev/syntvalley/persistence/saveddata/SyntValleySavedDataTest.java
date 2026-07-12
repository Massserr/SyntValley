package dev.syntvalley.persistence.saveddata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.syntvalley.persistence.codec.WorldStateCodec;
import dev.syntvalley.persistence.migration.MigrationRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SyntValleySavedDataTest {
    @Test
    void futureSchemaIsQuarantinedAndPreserved() {
        CompoundTag source = WorldStateCodec.encode(WorldState.createNew(10));
        source.putInt("schema_version", MigrationRegistry.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag before = source.copy();

        SyntValleySavedData savedData = SyntValleySavedData.loadFromTag(source);
        savedData.setDirty();

        assertFalse(savedData.isAvailable());
        assertFalse(savedData.isDirty());
        assertEquals(before, savedData.save(new CompoundTag(), null));
        assertEquals(before, source);
    }
}
