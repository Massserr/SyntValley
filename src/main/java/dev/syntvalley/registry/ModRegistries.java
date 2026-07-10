package dev.syntvalley.registry;

import java.util.Objects;
import net.neoforged.bus.api.IEventBus;

/**
 * Composition point for SyntValley deferred registries.
 *
 * <p>Slice 1 intentionally registers no placeholder content. Slice 2 will attach the first real
 * block, item, and block-entity registries here.</p>
 */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
    }
}
