package dev.syntvalley.network;

import dev.syntvalley.application.query.VillageOverviewDto;
import java.util.function.Consumer;

/**
 * Common indirection so the client-bound snapshot handler never classloads client-only code on a
 * dedicated server. The physical client installs the real handler during client setup.
 */
public final class ClientOverviewDispatch {
    private static volatile Consumer<VillageOverviewDto> handler = overview -> { };

    private ClientOverviewDispatch() {
    }

    public static void setHandler(Consumer<VillageOverviewDto> newHandler) {
        handler = newHandler;
    }

    public static void accept(VillageOverviewDto overview) {
        handler.accept(overview);
    }
}
