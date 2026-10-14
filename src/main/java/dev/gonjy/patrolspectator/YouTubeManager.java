package dev.gonjy.patrolspectator;

import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Maintains the YouTube Live connection, safe auto-replies and chat bridge.
 */
public final class YouTubeManager {
    private static final List<String> DEFAULT_IDLE_ANNOUNCEMENTS = List.of(
            "YouTubeのコメントはMinecraft内にも届きます。ゲームに入らなくても、配信チャットから参加者と会話できます！",
            "Minecraft内の公開チャットもYouTubeに表示されます。配信を見ながら気軽にコメントしてください！",
            "この配信は参加自由です。遊びたくなったら、概要欄のサーバー情報からいつでもご参加ください！",
            "見るだけでも、コメントだけでも大歓迎です。YouTubeからのコメントはゲーム内の参加者にも届きます！",
            "配信チャットからMinecraftの参加者へ話しかけられます。気になった場面があれば気軽にコメントしてください！",
            "Minecraftに入っている方の公開チャットは、このYouTubeチャットにも届きます。一緒に配信へ参加してみませんか？");

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
    private volatile String watchUrl;
    private YouTubeApiClient apiClient;
    private BukkitTask connectionTask;
    private BukkitTask pollingTask;
    private final YouTubeAutoReplyPolicy autoReplyPolicy =
            new YouTubeAutoReplyPolicy(Duration.ofMinutes(5), Duration.ofHours(6));
    private final Set<String> seenMessageIds = new LinkedHashSet<>();
    private String pageToken;
    private boolean initialChatPage = true;
    private final Map<String, Long> lastOutboundByPlayer = new HashMap<>();
    private long lastOutboundAt = Long.MIN_VALUE;
    private YouTubeIdleAnnouncementPolicy idleAnnouncementPolicy;
    private final YouTubeAnnouncementRotation idleAnnouncementRotation = new YouTubeAnnouncementRotation();

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
                plugin.getConfig().getInt("youtube.auto_reply.max_replies_per_day", 20));
        apiClient = new YouTubeApiClient(settings, quotaBudget);
        long idleMinutes = Math.max(1L,
                plugin.getConfig().getLong("youtube.idle_announcement.interval_minutes", 90L));
        idleAnnouncementPolicy = new YouTubeIdleAnnouncementPolicy(
                Duration.ofMinutes(idleMinutes), System.currentTimeMillis());
        update(Status.CONNECTING, null);
        long periodTicks = Math.max(1L,
                plugin.getConfig().getLong("youtube.connection_check_minutes", 5L)) * 60L * 20L;
        connectionTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshConnection, 1L, periodTicks);
    }

    private void refreshConnection() {
        try {
            YouTubeApiClient.ActiveBroadcast broadcast = apiClient.discoverActiveBroadcast();
            update(broadcast == null ? Status.NO_ACTIVE_STREAM : Status.CONNECTED, broadcast);
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

    private synchronized void update(Status nextStatus, YouTubeApiClient.ActiveBroadcast broadcast) {
        String nextLiveChatId = broadcast == null ? null : broadcast.liveChatId();
        boolean chatChanged = !java.util.Objects.equals(liveChatId, nextLiveChatId);
        boolean changed = status != nextStatus || chatChanged;
        if (chatChanged) resetPollingState();
        status = nextStatus;
        liveChatId = nextLiveChatId;
        watchUrl = broadcast == null ? null : broadcast.watchUrl();
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
            java.util.List<YouTubeApiClient.LiveChatMessage> freshMessages = new java.util.ArrayList<>();
            for (YouTubeApiClient.LiveChatMessage message : page.messages()) {
                if (!seenMessageIds.add(message.id()) || skipReplies) continue;
                freshMessages.add(message);
                if (!YouTubeChatBridgeFormatter.isPluginGenerated(message.text(), message.chatOwner())) {
                    idleAnnouncementPolicy.recordActivity(System.currentTimeMillis());
                }
                if (!plugin.getConfig().getBoolean("youtube.auto_reply.enabled", true)) continue;
                long now = System.currentTimeMillis();
                if (message.chatOwner()) continue;
                boolean replyToAny = plugin.getConfig().getBoolean(
                        "youtube.auto_reply.reply_to_any_comment", true);
                if (!autoReplyPolicy.shouldReply(message.authorChannelId(), message.text(), now, replyToAny)) continue;
                String replyText = plugin.getConfig().getString("youtube.auto_reply.message",
                        "ご視聴ありがとうございます！確認は不要です。概要欄のサーバー情報から、ご自由に参加してくださいね！");
                apiClient.insertTextMessage(liveChatId,
                        YouTubeChatBridgeFormatter.autoReply(message.authorName(), replyText), true);
                autoReplyPolicy.recordReply(message.authorChannelId(), now);
                plugin.getLogger().info("[YouTube] Sent one viewer auto-reply.");
            }
            trimSeenMessages();
            initialChatPage = false;
            pageToken = page.nextPageToken();
            relayYouTubeMessages(freshMessages, false);
            sendIdleAnnouncementIfNeeded();
            scheduleAdaptivePoll(page.pollingIntervalMillis());
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

    private void sendIdleAnnouncementIfNeeded() throws IOException, InterruptedException,
            YouTubeApiClient.ApiException {
        if (idleAnnouncementPolicy == null
                || !plugin.getConfig().getBoolean("youtube.idle_announcement.enabled", true)) return;
        long now = System.currentTimeMillis();
        if (!idleAnnouncementPolicy.shouldAnnounce(now)) return;
        List<String> configured = plugin.getConfig().getStringList("youtube.idle_announcement.messages");
        if (configured.isEmpty()) configured = DEFAULT_IDLE_ANNOUNCEMENTS;
        String formatted = YouTubeChatBridgeFormatter.announcement(
                idleAnnouncementRotation.next(configured));
        if (formatted == null) return;
        apiClient.insertTextMessage(liveChatId, formatted, true);
        idleAnnouncementPolicy.recordAnnouncement(now);
        plugin.getLogger().info("[YouTube] Sent one idle chat guidance message.");
    }

    private synchronized void scheduleNextPoll(long delayTicks) {
        if (pollingTask != null || status != Status.CONNECTED || liveChatId == null) return;
        pollingTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin, this::pollLiveChat, delayTicks);
    }

    private void relayYouTubeMessages(java.util.List<YouTubeApiClient.LiveChatMessage> messages,
                                      boolean skipInitialPage) {
        if (skipInitialPage || !plugin.getConfig().getBoolean("youtube.bridge.youtube_to_minecraft", true)) return;
        for (YouTubeApiClient.LiveChatMessage message : messages) {
            if (YouTubeChatBridgeFormatter.isPluginGenerated(message.text(), message.chatOwner())) continue;
            String formatted = YouTubeChatBridgeFormatter.toMinecraft(message.authorName(), message.text());
            if (formatted != null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getServer().broadcastMessage(formatted));
            }
        }
    }

    private void scheduleAdaptivePoll(long apiMinimumMillis) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            String cameraName = plugin.getConfig().getString("patrol.autoStart.cameraPlayerName", "OtouGame");
            boolean participantOnline = plugin.getServer().getOnlinePlayers().stream()
                    .anyMatch(player -> !player.getName().equalsIgnoreCase(cameraName));
            long configuredSeconds = participantOnline
                    ? plugin.getConfig().getLong("youtube.bridge.active_poll_interval_seconds", 30L)
                    : plugin.getConfig().getLong("youtube.bridge.idle_poll_interval_seconds", 120L);
            long nextDelayMillis = Math.max(Math.max(30L, configuredSeconds) * 1_000L, apiMinimumMillis);
            scheduleNextPoll(Math.max(20L, nextDelayMillis / 50L));
        });
    }

    private synchronized void resetPollingState() {
        cancelPollingTask();
        pageToken = null;
        initialChatPage = true;
        seenMessageIds.clear();
        if (idleAnnouncementPolicy != null) {
            idleAnnouncementPolicy.recordActivity(System.currentTimeMillis());
        }
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

    String watchUrl() {
        return status == Status.CONNECTED ? watchUrl : null;
    }

    public void sendPlayerMessage(String playerName, String message) {
        if (!plugin.getConfig().getBoolean("youtube.bridge.minecraft_to_youtube", true)) return;
        String formatted = YouTubeChatBridgeFormatter.toYouTube(playerName, message);
        if (formatted == null) return;

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (status != Status.CONNECTED || liveChatId == null || apiClient == null) return;
            long globalCooldown = Math.max(1L,
                    plugin.getConfig().getLong("youtube.bridge.global_send_cooldown_seconds", 5L)) * 1_000L;
            long playerCooldown = Math.max(globalCooldown,
                    plugin.getConfig().getLong("youtube.bridge.player_send_cooldown_seconds", 10L) * 1_000L);
            if (lastOutboundAt != Long.MIN_VALUE && now - lastOutboundAt < globalCooldown) return;
            Long playerLast = lastOutboundByPlayer.get(playerName.toLowerCase(java.util.Locale.ROOT));
            if (playerLast != null && now - playerLast < playerCooldown) return;
            lastOutboundAt = now;
            lastOutboundByPlayer.put(playerName.toLowerCase(java.util.Locale.ROOT), now);
            if (idleAnnouncementPolicy != null) idleAnnouncementPolicy.recordActivity(now);
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String chatId = liveChatId;
                if (chatId != null && status == Status.CONNECTED) apiClient.insertTextMessage(chatId, formatted, false);
            } catch (YouTubeApiClient.ApiException e) {
                update("QUOTA_LIMIT".equals(e.category()) ? Status.QUOTA_LIMIT
                        : "AUTH_ERROR".equals(e.category()) ? Status.AUTH_ERROR : Status.API_ERROR, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                update(Status.API_ERROR, null);
            } catch (IOException | RuntimeException e) {
                update(Status.API_ERROR, null);
            }
        });
    }
}
