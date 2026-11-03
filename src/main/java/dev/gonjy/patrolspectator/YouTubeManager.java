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
    private static final long INITIAL_CHAT_RECENT_WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long LEGACY_IDLE_ANNOUNCEMENT_MINUTES = 180L;
    private static final long DEFAULT_IDLE_ANNOUNCEMENT_MINUTES = 90L;
    private static final List<String> DEFAULT_IDLE_ANNOUNCEMENTS = List.of(
            "参加許可は不要です。初参加・途中参加も、ご自由にご参加ください！",
            "見かけたらそのまま入って大丈夫です。概要欄のサーバー情報から、ご勝手にご参加ください！",
            "この配信はいつでも自由参加です。遊びたくなった時に、お気軽にお入りください！",
            "『参加してもいいですか？』の確認は不要です。どなたでもご自由にどうぞ！",
            "YouTubeとMinecraftの公開チャットは双方向につながっています。参加者との会話もお楽しみください！",
            "ゲームに入らなくても、YouTubeのコメントから配信に参加できます。気軽に話しかけてください！");

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
    private volatile String activeVideoId;
    private volatile String watchUrl;
    private YouTubeApiClient apiClient;
    private BukkitTask connectionTask;
    private BukkitTask pollingTask;
    private BukkitTask streamStartTask;
    private final YouTubeAutoReplyPolicy autoReplyPolicy =
            new YouTubeAutoReplyPolicy(Duration.ofMinutes(5), Duration.ofHours(6));
    private final Set<String> seenMessageIds = new LinkedHashSet<>();
    private String pageToken;
    private boolean initialChatPage = true;
    private long chatConnectedAtMillis;
    private final Map<String, Long> lastOutboundByPlayer = new HashMap<>();
    private long lastOutboundAt = Long.MIN_VALUE;
    private YouTubeIdleAnnouncementPolicy idleAnnouncementPolicy;
    private final YouTubeAnnouncementRotation idleAnnouncementRotation = new YouTubeAnnouncementRotation();
    private final YouTubeStreamStartState streamStartState;
    private boolean streamStartAlreadyAnnouncedLogged;
    private volatile YouTubeConnectionGuide connectionGuide =
            new YouTubeConnectionGuide(null, null, null);

    public YouTubeManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        streamStartState = new YouTubeStreamStartState(plugin.getDataFolder().toPath());
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
        refreshConnectionGuide();
        long configuredIdleMinutes = plugin.getConfig().getLong(
                "youtube.idle_announcement.interval_minutes", DEFAULT_IDLE_ANNOUNCEMENT_MINUTES);
        long idleMinutes = resolveIdleAnnouncementMinutes(configuredIdleMinutes);
        if (configuredIdleMinutes == LEGACY_IDLE_ANNOUNCEMENT_MINUTES) {
            plugin.getConfig().set("youtube.idle_announcement.interval_minutes", idleMinutes);
            plugin.saveConfig();
            plugin.getLogger().info("[YouTube] 無言時案内の旧標準間隔を180分から90分へ移行しました。");
        }
        idleAnnouncementPolicy = new YouTubeIdleAnnouncementPolicy(
                Duration.ofMinutes(idleMinutes), System.currentTimeMillis());
        plugin.getLogger().info("[YouTube] 無言時案内の間隔: " + idleMinutes + "分");
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
            if (failureStatus == Status.AUTH_ERROR && status != Status.AUTH_ERROR) {
                plugin.getLogger().warning(authFailureMessage(e));
            }
            update(failureStatus, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(Status.API_ERROR, null);
        } catch (IOException | RuntimeException e) {
            update(Status.API_ERROR, null);
        }
    }

    static String authFailureMessage(YouTubeApiClient.ApiException exception) {
        String guidance = switch (exception.reason() == null ? "" : exception.reason()) {
            case "invalid_client" -> "clientId と clientSecret が同じ有効なOAuthクライアントの値か確認してください。";
            case "invalid_grant" -> "refreshToken が失効・取消済み、または別のOAuthクライアントで発行されていないか確認してください。";
            case "unauthorized_client" -> "OAuthクライアントの種類と許可設定を確認してください。";
            case "access_denied" -> "Googleアカウント側でYouTubeアクセスが許可されているか確認してください。";
            case "temporarily_unavailable" -> "Google OAuthが一時的に利用できません。時間を置いて再試行してください。";
            default -> "clientId・clientSecret・refreshTokenの組み合わせを確認してください。";
        };
        String reason = exception.reason() == null ? "unknown" : exception.reason();
        return "[YouTube] OAuth認証に失敗しました (HTTP " + exception.statusCode()
                + ", reason=" + reason + ")。" + guidance;
    }

    private synchronized void update(Status nextStatus, YouTubeApiClient.ActiveBroadcast broadcast) {
        String nextLiveChatId = broadcast == null ? null : broadcast.liveChatId();
        String nextVideoId = broadcast == null ? null : broadcast.videoId();
        boolean chatChanged = !java.util.Objects.equals(liveChatId, nextLiveChatId);
        boolean videoChanged = !java.util.Objects.equals(activeVideoId, nextVideoId);
        boolean changed = status != nextStatus || chatChanged;
        if (chatChanged) resetPollingState();
        if (videoChanged) {
            cancelStreamStartTask();
            streamStartAlreadyAnnouncedLogged = false;
        }
        status = nextStatus;
        liveChatId = nextLiveChatId;
        activeVideoId = nextVideoId;
        watchUrl = broadcast == null ? null : broadcast.watchUrl();
        if (changed) {
            plugin.getLogger().info("[YouTube] Connection status: " + nextStatus);
        }
        if (nextStatus == Status.CONNECTED && nextLiveChatId != null) {
            scheduleNextPoll(1L);
            scheduleStreamStartAnnouncement(nextVideoId);
        } else {
            cancelPollingTask();
            cancelStreamStartTask();
        }
    }

    private synchronized void scheduleStreamStartAnnouncement(String videoId) {
        if (!plugin.getConfig().getBoolean("youtube.stream_start_announcement.enabled", true)
                || streamStartTask != null) return;
        if (!streamStartState.needsAnnouncement(videoId)) {
            if (!streamStartAlreadyAnnouncedLogged && videoId != null && !videoId.isBlank()) {
                plugin.getLogger().info("[YouTube] この配信には開始時の参加案内を送信済みです。重複投稿を省略します。");
                streamStartAlreadyAnnouncedLogged = true;
            }
            return;
        }
        long delaySeconds = Math.max(1L,
                plugin.getConfig().getLong("youtube.stream_start_announcement.delay_seconds", 90L));
        streamStartTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin, () -> sendStreamStartAnnouncement(videoId), delaySeconds * 20L);
    }

    static long resolveIdleAnnouncementMinutes(long configuredMinutes) {
        if (configuredMinutes == LEGACY_IDLE_ANNOUNCEMENT_MINUTES) {
            return DEFAULT_IDLE_ANNOUNCEMENT_MINUTES;
        }
        return Math.max(1L, configuredMinutes);
    }

    private void sendStreamStartAnnouncement(String videoId) {
        synchronized (this) {
            streamStartTask = null;
            if (status != Status.CONNECTED || liveChatId == null
                    || !java.util.Objects.equals(activeVideoId, videoId)
                    || !streamStartState.needsAnnouncement(videoId)) return;
        }

        try {
            String message = plugin.getConfig().getString("youtube.stream_start_announcement.message",
                    "参加許可は不要です。初参加・途中参加も、概要欄のサーバー情報からご自由にご参加ください！");
            String formatted = YouTubeChatBridgeFormatter.announcement(withConnectionGuide(message));
            if (formatted == null) return;
            String chatId = liveChatId;
            if (chatId == null) return;
            apiClient.insertTextMessage(chatId, formatted, true);
            try {
                streamStartState.markAnnounced(videoId);
            } catch (IOException e) {
                plugin.getLogger().warning("[YouTube] Stream-start guidance was sent, but its state could not be saved.");
            }
            if (idleAnnouncementPolicy != null) {
                idleAnnouncementPolicy.recordAnnouncement(System.currentTimeMillis());
            }
            plugin.getLogger().info("[YouTube] Sent the one-time stream-start participation guidance.");
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

    private void pollLiveChat() {
        synchronized (this) {
            pollingTask = null;
            if (status != Status.CONNECTED || liveChatId == null) return;
        }

        try {
            YouTubeApiClient.LiveChatPage page = apiClient.listLiveChatMessages(liveChatId, pageToken);
            boolean skipReplies = initialChatPage;
            long now = System.currentTimeMillis();
            int skippedHistory = 0;
            int skippedOwner = 0;
            int skippedPolicy = 0;
            java.util.List<YouTubeApiClient.LiveChatMessage> freshMessages = new java.util.ArrayList<>();
            for (YouTubeApiClient.LiveChatMessage message : page.messages()) {
                if (!seenMessageIds.add(message.id())) continue;
                if (skipReplies && !isRecentInitialMessage(message, chatConnectedAtMillis, now)) {
                    skippedHistory++;
                    continue;
                }
                freshMessages.add(message);
                if (!YouTubeChatBridgeFormatter.isPluginGenerated(message.text(), message.chatOwner())) {
                    idleAnnouncementPolicy.recordActivity(System.currentTimeMillis());
                }
                if (!plugin.getConfig().getBoolean("youtube.auto_reply.enabled", true)) continue;
                if (message.chatOwner()) {
                    skippedOwner++;
                    continue;
                }
                boolean replyToAny = plugin.getConfig().getBoolean(
                        "youtube.auto_reply.reply_to_any_comment", true);
                if (!autoReplyPolicy.shouldReply(message.authorChannelId(), message.text(), now, replyToAny)) {
                    skippedPolicy++;
                    continue;
                }
                String replyText = plugin.getConfig().getString("youtube.auto_reply.message",
                        "ご視聴ありがとうございます！確認は不要です。概要欄のサーバー情報から、ご自由に参加してくださいね！");
                apiClient.insertTextMessage(liveChatId,
                        YouTubeChatBridgeFormatter.autoReply(message.authorName(),
                                withConnectionGuide(replyText)), true);
                autoReplyPolicy.recordReply(message.authorChannelId(), now);
                plugin.getLogger().info("[YouTube] Sent one viewer auto-reply.");
            }
            trimSeenMessages();
            initialChatPage = false;
            if (skippedHistory + skippedOwner + skippedPolicy > 0) {
                plugin.getLogger().info("[YouTube] Auto-reply summary: history=" + skippedHistory
                        + ", owner=" + skippedOwner + ", cooldown_or_policy=" + skippedPolicy + ".");
            }
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

    static boolean isRecentInitialMessage(YouTubeApiClient.LiveChatMessage message,
                                          long connectedAtMillis, long nowMillis) {
        Long publishedAt = message.publishedAtMillis();
        return publishedAt != null
                && publishedAt >= connectedAtMillis - INITIAL_CHAT_RECENT_WINDOW_MILLIS
                && publishedAt <= nowMillis + Duration.ofMinutes(1).toMillis();
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
                withConnectionGuide(idleAnnouncementRotation.next(configured)));
        if (formatted == null) return;
        apiClient.insertTextMessage(liveChatId, formatted, true);
        idleAnnouncementPolicy.recordAnnouncement(now);
        plugin.getLogger().info("[YouTube] Sent one idle chat guidance message.");
    }

    private String withConnectionGuide(String message) {
        if (!plugin.getConfig().getBoolean("youtube.connection_guide.enabled", true)) return message;
        return connectionGuide.appendTo(message);
    }

    private void refreshConnectionGuide() {
        String javaAddress = plugin.getConfig().getString("youtube.connection_guide.java_address",
                plugin.getConfig().getString("discord.server_ip", ""));
        connectionGuide = new YouTubeConnectionGuide(javaAddress, null, null);
        FalixApiSettings falixSettings = FalixApiSettings.load(plugin.getDataFolder().toPath());
        if (!falixSettings.configured()) {
            plugin.getLogger().info("[YouTube] 接続案内はJava版のみ掲載します（Falix API未設定）。");
            return;
        }
        new FalixApiClient().inspect(falixSettings).thenAccept(result -> {
            connectionGuide = connectionGuide.withFalix(result);
            if (connectionGuide.text().contains("BE版:")) {
                plugin.getLogger().info("[YouTube] Java版・BE版の公開接続先を参加案内へ掲載します。");
            } else {
                plugin.getLogger().warning("[YouTube] BE版の公開接続先を取得できないため、Java版のみ掲載します。");
            }
        });
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
        chatConnectedAtMillis = System.currentTimeMillis();
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

    private synchronized void cancelStreamStartTask() {
        if (streamStartTask != null) {
            streamStartTask.cancel();
            streamStartTask = null;
        }
    }

    public void stop() {
        if (connectionTask != null) {
            connectionTask.cancel();
            connectionTask = null;
        }
        cancelPollingTask();
        cancelStreamStartTask();
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
