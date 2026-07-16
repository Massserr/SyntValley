package dev.syntvalley.network;

import dev.syntvalley.application.query.VillageLogPage;
import java.util.function.Consumer;

/**
 * Common indirection so the client-bound log-page handler never classloads client-only code on a
 * dedicated server. The physical client installs the real handler during client setup.
 */
public final class ClientLogDispatch {
    private static volatile Consumer<VillageLogPage> handler = page -> { };

    private ClientLogDispatch() {
    }

    public static void setHandler(Consumer<VillageLogPage> newHandler) {
        handler = newHandler;
    }

    public static void accept(VillageLogPage page) {
        handler.accept(page);
    }
}
