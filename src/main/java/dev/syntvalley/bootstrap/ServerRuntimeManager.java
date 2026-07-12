package dev.syntvalley.bootstrap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * Technical lifecycle registry only. Values contain services/bookkeeping; canonical Village state
 * is exclusively owned by each server's Overworld SavedData.
 */
public final class ServerRuntimeManager {
    private static final Map<MinecraftServer, SyntValleyServerRuntime> RUNTIMES = new IdentityHashMap<>();

    private ServerRuntimeManager() {
    }

    public static synchronized SyntValleyServerRuntime getOrCreate(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return RUNTIMES.computeIfAbsent(server, SyntValleyServerRuntime::new);
    }

    public static synchronized Optional<SyntValleyServerRuntime> find(MinecraftServer server) {
        return Optional.ofNullable(RUNTIMES.get(Objects.requireNonNull(server, "server")));
    }

    public static synchronized Optional<SyntValleyServerRuntime> remove(MinecraftServer server) {
        return Optional.ofNullable(RUNTIMES.remove(Objects.requireNonNull(server, "server")));
    }
}
