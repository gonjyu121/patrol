package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

final class ServerDiagnosticsService {
    private final PatrolSpectatorPlugin plugin;
    private final PatrolManager patrolManager;

    ServerDiagnosticsService(PatrolSpectatorPlugin plugin, PatrolManager patrolManager) {
        this.plugin = plugin;
        this.patrolManager = patrolManager;
    }

    void run(CommandSender sender) {
        plugin.getLogger().info("[Diagnostics] Started by " + sender.getName());
        ServerDiagnosticsReport report = collect();
        report.displayLines().forEach(sender::sendMessage);
        plugin.getLogger().info("[Diagnostics] Server=" + report.serverStatus());
        plugin.getLogger().info("[Diagnostics] Geyser=" + report.geyserStatus()
                + " UDP=" + valueOrUnknown(report.geyserUdpPort()));
        plugin.getLogger().info("[Diagnostics] Floodgate=" + report.floodgateStatus());
        plugin.getLogger().info("[Diagnostics] BedrockPlayers=" + valueOrUnknown(report.bedrockPlayers()));
        plugin.getLogger().info("[Diagnostics] Overall=" + report.overall());
    }

    ServerDiagnosticsReport collect() {
        Plugin geyser = findPlugin("Geyser-Spigot", "Geyser");
        Plugin floodgate = findPlugin("floodgate", "Floodgate");

        String geyserStatus = pluginStatus(geyser);
        String floodgateStatus = pluginStatus(floodgate);
        Integer udpPort = null;
        Integer bedrockPlayers = null;

        if (geyser != null && geyser.isEnabled()) {
            try {
                GeyserDiagnosticsProbe.Result result = GeyserDiagnosticsProbe.inspect();
                udpPort = result.udpPort();
                bedrockPlayers = result.bedrockPlayers();
                if (udpPort == null) geyserStatus = "WARNING";
            } catch (LinkageError | RuntimeException ex) {
                geyserStatus = "WARNING";
            }
        }

        if (bedrockPlayers == null && floodgate != null && floodgate.isEnabled()) {
            try {
                bedrockPlayers = FloodgateDiagnosticsProbe.onlineBedrockPlayers();
            } catch (LinkageError | RuntimeException ignored) {
                // Optional API unavailable. The report intentionally keeps this UNKNOWN.
            }
        }

        boolean coreInitialized = plugin.areCoreSystemsInitialized();
        String overall = "OK";
        if (!coreInitialized || !"OK".equals(geyserStatus) || !"OK".equals(floodgateStatus)
                || udpPort == null || bedrockPlayers == null) {
            overall = "WARNING";
        }

        return new ServerDiagnosticsReport(
                "OK",
                Bukkit.getServer().getPort(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getWorlds().stream().map(World::getName).toList(),
                plugin.getDescription().getVersion(),
                patrolManager.isRunning(),
                patrolManager.getCameraPlayer() == null ? "NONE" : patrolManager.getCameraPlayer().getName(),
                coreInitialized,
                geyserStatus,
                pluginVersion(geyser),
                udpPort,
                floodgateStatus,
                pluginVersion(floodgate),
                bedrockPlayers,
                overall);
    }

    private Plugin findPlugin(String... names) {
        List<String> accepted = Arrays.stream(names).map(String::toLowerCase).toList();
        return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(candidate -> accepted.contains(candidate.getName().toLowerCase()))
                .findFirst().orElse(null);
    }

    private static String pluginStatus(Plugin dependency) {
        if (dependency == null) return "NOT INSTALLED";
        return dependency.isEnabled() ? "OK" : "DISABLED";
    }

    private static String pluginVersion(Plugin dependency) {
        return dependency == null ? "NOT INSTALLED" : dependency.getDescription().getVersion();
    }

    private static String valueOrUnknown(Integer value) {
        return value == null ? "UNKNOWN" : Integer.toString(value);
    }
}
