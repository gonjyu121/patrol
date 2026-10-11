package dev.gonjy.patrolspectator;

import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;

/**
 * Maintains the read-only YouTube Live connection state.
 * Message reading and posting are intentionally implemented in later issues.
 */
public final class YouTubeManager {
    enum Status {
        DISABLED,
        NOT_CONFIGURED,
        CONNECTING,
        CONNECTED,
        NO_ACTIVE_STREAM,
        MULTIPLE_ACTIVE_STREAMS,
        AUTH_ERROR,
        API_ERROR
    }

    private final PatrolSpectatorPlugin plugin;
    private volatile Status status = Status.DISABLED;
    private volatile String liveChatId;
    private YouTubeApiClient apiClient;
    private BukkitTask connectionTask;

    public YouTubeManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("youtube.enabled", false)) {
            update(Status.DISABLED, null);
            return;
        }

        YouTubeApiSettings settings = YouTubeApiSettings.load(plugin.getDataFolder().toPath());
        if (!settings.configured()) {
            update(Status.NOT_CONFIGURED, null);
            plugin.getLogger().warning("[YouTube] Enabled, but external OAuth settings are not configured. Integration remains inactive.");
            return;
        }

        apiClient = new YouTubeApiClient(settings);
        update(Status.CONNECTING, null);
        long periodTicks = Math.max(1L,
                plugin.getConfig().getLong("youtube.connection_check_minutes", 5L)) * 60L * 20L;
        connectionTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshConnection, 1L, periodTicks);
    }

    private void refreshConnection() {
        try {
            String discoveredLiveChatId = apiClient.discoverActiveLiveChatId();
            update(discoveredLiveChatId == null ? Status.NO_ACTIVE_STREAM : Status.CONNECTED,
                    discoveredLiveChatId);
        } catch (YouTubeApiClient.ApiException e) {
            Status failureStatus = switch (e.category()) {
                case "AUTH_ERROR" -> Status.AUTH_ERROR;
                case "MULTIPLE_ACTIVE_STREAMS" -> Status.MULTIPLE_ACTIVE_STREAMS;
                default -> Status.API_ERROR;
            };
            update(failureStatus, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(Status.API_ERROR, null);
        } catch (IOException | RuntimeException e) {
            update(Status.API_ERROR, null);
        }
    }

    private synchronized void update(Status nextStatus, String nextLiveChatId) {
        boolean changed = status != nextStatus || !java.util.Objects.equals(liveChatId, nextLiveChatId);
        status = nextStatus;
        liveChatId = nextLiveChatId;
        if (changed) {
            plugin.getLogger().info("[YouTube] Connection status: " + nextStatus);
        }
    }

    public void stop() {
        if (connectionTask != null) {
            connectionTask.cancel();
            connectionTask = null;
        }
    }

    public boolean isEnabled() {
        return status == Status.CONNECTED;
    }

    Status status() {
        return status;
    }

    String liveChatId() {
        return liveChatId;
    }

    public void sendMessage(String message) {
        // YouTube message posting is intentionally deferred to Issues #77 and #78.
    }
}
