package dev.gonjy.patrolspectator;

import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

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
        QUOTA_LIMIT,
        AUTH_ERROR,
        API_ERROR
    }

    private final PatrolSpectatorPlugin plugin;
    private volatile Status status = Status.DISABLED;
    private volatile String liveChatId;
    private YouTubeApiClient apiClient;
    private BukkitTask connectionTask;
    private BukkitTask pollingTask;
    private final YouTubeAutoReplyPolicy autoReplyPolicy =
            new YouTubeAutoReplyPolicy(Duration.ofMinutes(5), Duration.ofHours(6));
    private final Set<String> seenMessageIds = new LinkedHashSet<>();
    private String pageToken;
    private boolean initialChatPage = true;

    private static final String AUTO_REPLY_TEXT =
            "参加歓迎です！確認不要で、概要欄のサーバー情報からいつでも自由に入って遊んでください！";

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

        YouTubeQuotaBudget quotaBudget = new YouTubeQuotaBudget(
                plugin.getDataFolder().toPath(),
                plugin.getConfig().getInt("youtube.daily_quota_unit_limit", 8_000),
                plugin.getConfig().getInt("youtube.auto_reply.max_replies_per_day", 10));
        apiClient = new YouTubeApiClient(settings, quotaBudget);
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
                case "QUOTA_LIMIT" -> Status.QUOTA_LIMIT;
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
        boolean chatChanged = !java.util.Objects.equals(liveChatId, nextLiveChatId);
        boolean changed = status != nextStatus || chatChanged;
        if (chatChanged) resetPollingState();
        status = nextStatus;
        liveChatId = nextLiveChatId;
        if (changed) {
            plugin.getLogger().info("[YouTube] Connection status: " + nextStatus);
        }
        if (nextStatus == Status.CONNECTED && nextLiveChatId != null) scheduleNextPoll(1L);
        else cancelPollingTask();
    }

    private void pollLiveChat() {
        synchronized (this) {
            pollingTask = null;
            if (status != Status.CONNECTED || liveChatId == null) return;
        }

        try {
            YouTubeApiClient.LiveChatPage page = apiClient.listLiveChatMessages(liveChatId, pageToken);
            boolean skipReplies = initialChatPage;
            for (YouTubeApiClient.LiveChatMessage message : page.messages()) {
                if (!seenMessageIds.add(message.id()) || skipReplies) continue;
                if (!plugin.getConfig().getBoolean("youtube.auto_reply.enabled", true)) continue;
                long now = System.currentTimeMillis();
                if (!autoReplyPolicy.shouldReply(message.authorChannelId(), message.text(), now)) continue;
                apiClient.insertTextMessage(liveChatId, replyText(message.authorName()));
                autoReplyPolicy.recordReply(message.authorChannelId(), now);
                plugin.getLogger().info("[YouTube] Sent one participation auto-reply.");
            }
            trimSeenMessages();
            initialChatPage = false;
            pageToken = page.nextPageToken();
            long configuredMinimumMillis = Math.max(60L,
                    plugin.getConfig().getLong("youtube.poll_interval_seconds", 60L)) * 1_000L;
            long nextDelayMillis = Math.max(configuredMinimumMillis, page.pollingIntervalMillis());
            scheduleNextPoll(Math.max(20L, nextDelayMillis / 50L));
        } catch (YouTubeApiClient.ApiException e) {
            update("QUOTA_LIMIT".equals(e.category()) ? Status.QUOTA_LIMIT
                    : "AUTH_ERROR".equals(e.category()) ? Status.AUTH_ERROR : Status.API_ERROR, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(Status.API_ERROR, null);
        } catch (IOException | RuntimeException e) {
            update(Status.API_ERROR, null);
        }
    }

    private synchronized void scheduleNextPoll(long delayTicks) {
        if (pollingTask != null || status != Status.CONNECTED || liveChatId == null) return;
        pollingTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin, this::pollLiveChat, delayTicks);
    }

    private String replyText(String authorName) {
        if (authorName == null || authorName.isBlank()) return AUTO_REPLY_TEXT;
        String safeName = authorName.replaceAll("[\\p{Cntrl}\\r\\n]", "").trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        return safeName.isBlank() ? AUTO_REPLY_TEXT : "@" + safeName + " " + AUTO_REPLY_TEXT;
    }

    private synchronized void resetPollingState() {
        cancelPollingTask();
        pageToken = null;
        initialChatPage = true;
        seenMessageIds.clear();
    }

    private void trimSeenMessages() {
        while (seenMessageIds.size() > 1_000) {
            String oldest = seenMessageIds.iterator().next();
            seenMessageIds.remove(oldest);
        }
    }

    private synchronized void cancelPollingTask() {
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
        }
    }

    public void stop() {
        if (connectionTask != null) {
            connectionTask.cancel();
            connectionTask = null;
        }
        cancelPollingTask();
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
