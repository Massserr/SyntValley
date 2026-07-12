package dev.syntvalley.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.persistence.codec.PersistenceException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MigrationRegistryTest {
    @Test
    void currentSchemaReturnsDefensiveCopyWithoutMigration() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 1);

        MigrationResult result = MigrationRegistry.current().migrate(source);

        assertFalse(result.migrated());
        assertEquals(source, result.root());
        assertNotSame(source, result.root());
    }

    @Test
    void migrationChainIsSequentialAndDoesNotMutateSource() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 1);
        source.putString("name", "before");
        CompoundTag before = source.copy();
        MigrationRegistry registry = new MigrationRegistry(
                2,
                Map.of(1, tag -> {
                    tag.putString("name", "after");
                    return tag;
                })
        );

        MigrationResult result = registry.migrate(source);

        assertTrue(result.migrated());
        assertEquals(2, result.root().getInt("schema_version"));
        assertEquals("after", result.root().getString("name"));
        assertEquals(before, source);
    }

    @Test
    void missingMigrationFailsClosed() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 1);
        MigrationRegistry registry = new MigrationRegistry(2, Map.of());

        assertThrows(PersistenceException.class, () -> registry.migrate(source));
    }
}
