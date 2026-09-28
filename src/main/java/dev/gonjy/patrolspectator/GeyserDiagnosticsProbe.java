package dev.gonjy.patrolspectator;

import org.geysermc.geyser.api.GeyserApi;

/** Optional Geyser public-API access. This class is loaded only when Geyser is enabled. */
final class GeyserDiagnosticsProbe {
    private GeyserDiagnosticsProbe() {
    }

    static Result inspect() {
        GeyserApi api = GeyserApi.api();
        if (api == null) return new Result(null, null);
        return new Result(api.bedrockListener().port(), api.onlineConnectionsCount());
    }

    record Result(Integer udpPort, Integer bedrockPlayers) {
    }
}
