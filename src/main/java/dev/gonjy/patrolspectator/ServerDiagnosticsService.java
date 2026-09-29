package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ServerDiagnosticsService {
    private final PatrolSpectatorPlugin plugin;
    private final PatrolManager patrolManager;
    private final FalixApiClient falixApiClient;
    private FalixDiagnosticsResult cachedFalix;
    private long cacheExpiresAt;

    ServerDiagnosticsService(PatrolSpectatorPlugin plugin, PatrolManager patrolManager) {
        this(plugin, patrolManager, new FalixApiClient());
    }

    ServerDiagnosticsService(PatrolSpectatorPlugin plugin, PatrolManager patrolManager, FalixApiClient falixApiClient) {
        this.plugin = plugin;
        this.patrolManager = patrolManager;
        this.falixApiClient = falixApiClient;
    }

    void run(CommandSender sender) {
        plugin.getLogger().info("[Diagnostics] Started by " + sender.getName());
        LocalDiagnostics local = collectLocal();
        FalixApiSettings settings = FalixApiSettings.load(plugin.getDataFolder().toPath());
        if (!settings.configured()) {
            deliver(sender, local.withFalix(FalixDiagnosticsResult.notConfigured()));
            return;
        }

        if (cachedFalix != null && System.currentTimeMillis() < cacheExpiresAt) {
            deliver(sender, local.withFalix(cachedFalix));
            return;
        }

        sender.sendMessage("§7[Patrol] Falix APIを確認しています...");
        CompletableFuture<FalixDiagnosticsResult> future = falixApiClient.inspect(settings);
        future.thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            cachedFalix = result;
            cacheExpiresAt = System.currentTimeMillis() + 60_000L;
            deliver(sender, local.withFalix(result));
        }));
    }

    private void deliver(CommandSender sender, ServerDiagnosticsReport report) {
        report.displayLines().forEach(sender::sendMessage);
        plugin.getLogger().info("[Diagnostics] Server=" + report.serverStatus());
        plugin.getLogger().info("[Diagnostics] Geyser=" + report.geyserStatus()
                + " UDP=" + valueOrUnknown(report.geyserUdpPort()));
        plugin.getLogger().info("[Diagnostics] Floodgate=" + report.floodgateStatus());
        plugin.getLogger().info("[Diagnostics] BedrockPlayers=" + valueOrUnknown(report.bedrockPlayers()));
        plugin.getLogger().info("[Diagnostics] FalixApi=" + report.falixApiStatus()
                + " Primary=" + valueOrUnknown(report.falixPrimaryPort())
                + " Geyser=" + valueOrUnknown(report.falixGeyserPort())
                + " PortMatch=" + report.portMatch());
        plugin.getLogger().info("[Diagnostics] Overall=" + report.overall());
    }

    ServerDiagnosticsReport collect() {
        return collectLocal().withFalix(FalixDiagnosticsResult.notConfigured());
    }

    private LocalDiagnostics collectLocal() {
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
        return new LocalDiagnostics(
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
                bedrockPlayers);
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

    private record LocalDiagnostics(
            String serverStatus, int paperPort, int onlinePlayers, List<String> worlds,
            String patrolVersion, boolean patrolRunning, String camera, boolean coreInitialized,
            String geyserStatus, String geyserVersion, Integer geyserUdpPort,
            String floodgateStatus, String floodgateVersion, Integer bedrockPlayers) {

        ServerDiagnosticsReport withFalix(FalixDiagnosticsResult falix) {
            String portMatch = "UNKNOWN";
            if (falix.geyserPort() != null && geyserUdpPort != null) {
                portMatch = falix.geyserPort().equals(geyserUdpPort) ? "OK" : "MISMATCH";
            }
            boolean healthy = coreInitialized && "OK".equals(geyserStatus) && "OK".equals(floodgateStatus)
                    && geyserUdpPort != null && bedrockPlayers != null
                    && "OK".equals(falix.apiStatus()) && "OK".equals(portMatch);
            return new ServerDiagnosticsReport(serverStatus, paperPort, onlinePlayers, worlds,
                    patrolVersion, patrolRunning, camera, coreInitialized, geyserStatus, geyserVersion,
                    geyserUdpPort, floodgateStatus, floodgateVersion, bedrockPlayers,
                    falix.apiStatus(), falix.address(), falix.primaryPort(), falix.geyserPort(), portMatch,
                    healthy ? "OK" : "WARNING");
        }
    }
}
