package dev.gonjy.patrolspectator;

import org.geysermc.floodgate.api.FloodgateApi;

/** Optional Floodgate public-API access. This class is loaded only when Floodgate is enabled. */
final class FloodgateDiagnosticsProbe {
    private FloodgateDiagnosticsProbe() {
    }

    static int onlineBedrockPlayers() {
        return FloodgateApi.getInstance().getPlayerCount();
    }
}
