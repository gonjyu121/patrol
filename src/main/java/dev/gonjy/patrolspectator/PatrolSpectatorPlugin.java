package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PatrolSpectatorPlugin extends JavaPlugin {

    // サブモジュール
    private AutoEventSystem autoEventSystem;
    private EngagementSystem engagementSystem;
    private GameModeEnforcer gameModeEnforcer;
    private ParticipationManager participationManager;
    private PatrolManager patrolManager;
    private RankingDisplaySystem rankingDisplaySystem;

    private EndResetManager endResetManager;
    private YouTubeManager youTubeManager;
    private DiscordWebhookClient discordWebhookClient;
    private BountyManager bountyManager;
    private EndGameManager endGameManager;

    // タイトル/音の設定
    public static class TitleConf {
        public boolean enabled;
        public int fadeIn;
        public int stay;
        public int fadeOut;
    }

    public static class SoundConf {
        public boolean enabled;
        public String type;
        public float volume;
        public float pitch;
    }

    private TitleConf titleConf;
    private SoundConf spectateSoundConf;

    // 観光巡り config
    public static class TourConf {
        public boolean enabled;
        public int dwellSeconds;
        public String file;
        public boolean useArmorStandPOIs;
        public String armorStandTag;
        public int autogenPoints;
        public int autogenRadius;
        public double autogenYOffset;
    }

    public static class PerformanceConf {
        public boolean lowSpecMode;
        public int minIntervalSeconds;
        public int maxCameraMovesPerMinute;
        public boolean disableWorldScan;
        public boolean disableAutoEventWhilePatrol;
        public boolean debugLog;
    }

    private TourConf tourConf;
    private PerformanceConf performanceConf;
    private int patrolIntervalSeconds;
    @SuppressWarnings("unused") // Reserved for future use, loaded from config
    private boolean announce;

    // 保護情報
    private ProtectionData protectionData;

    // 参加回数・ランキング
    private PlayerStatsStorage statsStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        // 保護データの初期化
        protectionData = new ProtectionData(this);

        // ストレージ
        statsStorage = new PlayerStatsStorage(this);

        // サブシステム初期化
        // Discord Integration (Early init for dependency injection)
        discordWebhookClient = new DiscordWebhookClient(this);
        discordWebhookClient.reload();

        engagementSystem = new EngagementSystem(this);
        gameModeEnforcer = new GameModeEnforcer(this);
        autoEventSystem = new AutoEventSystem(this);
        participationManager = new ParticipationManager(this, statsStorage);
        rankingDisplaySystem = new RankingDisplaySystem(this, statsStorage);
        endResetManager = new EndResetManager(this);
        endGameManager = new EndGameManager(this, statsStorage, discordWebhookClient);

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new RankingEventListener(statsStorage), this);

        // PatrolManagerの初期化（依存関係を注入）
        patrolManager = new PatrolManager(this, engagementSystem, participationManager, gameModeEnforcer,
                rankingDisplaySystem);

        // ルール適用（Bedrock/Java 1.21.11+ 対応）
        engagementSystem.applyServerRules();

        // 観光地ロード
        patrolManager.loadTouristLocations();

        // MessageUtils初期化
        MessageUtils.init(titleConf);

        // コマンド登録
        // コマンド登録
        PatrolCommand patrolCmd = new PatrolCommand(this, patrolManager, rankingDisplaySystem);
        getCommand("patrol").setExecutor(patrolCmd);
        getCommand("patrol").setTabCompleter(patrolCmd);

        // 自動イベントシステムの開始
        autoEventSystem.startAutoEvents();

        // ルール定期適用タスク（1分間隔）
        getServer().getScheduler().runTaskTimer(this, () -> {
            engagementSystem.applyServerRulesQuietly();
        }, 1200L, 1200L);

        if (getConfig().getBoolean("discord.enabled", false)) {
            getServer().getPluginManager().registerEvents(new DiscordListener(this, discordWebhookClient), this);
            getLogger().info("[Discord] Integration enabled.");
        }

        // YouTube Integration
        youTubeManager = new YouTubeManager(this);
        if (getConfig().getBoolean("youtube.enabled", false)) {
            String clientId = getConfig().getString("youtube.client_id");
            String clientSecret = getConfig().getString("youtube.client_secret");
            String refreshToken = getConfig().getString("youtube.refresh_token");
            youTubeManager.initialize(clientId, clientSecret, refreshToken);
            getServer().getPluginManager().registerEvents(new YouTubeChatListener(this, youTubeManager), this);
        }

        // Bounty System
        bountyManager = new BountyManager(this);
        getCommand("bounty").setExecutor(new BountyCommand(bountyManager));

        getLogger().info("PatrolSpectatorPlugin enabled.");
    }

    public DiscordWebhookClient getDiscordWebhookClient() {
        return discordWebhookClient;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    @Override
    public void onDisable() {
        if (patrolManager != null) {
            patrolManager.stopPatrol();
        }
        if (autoEventSystem != null)
            autoEventSystem.shutdown();
        if (gameModeEnforcer != null)
            gameModeEnforcer.shutdown();
        if (endGameManager != null)
            endGameManager.shutdown();

        // 最後にストレージ保存
        if (statsStorage != null) {
            statsStorage.flush();
        }
        getLogger().info("PatrolSpectatorPlugin disabled.");
    }

    private void loadConfigValues() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        patrolIntervalSeconds = getConfig().getInt("patrol.intervalSeconds", 10);
        announce = getConfig().getBoolean("patrol.announce", true); // 予約・今は未使用

        // titles
        titleConf = new TitleConf();
        titleConf.enabled = getConfig().getBoolean("patrol.titles.enabled", true);
        titleConf.fadeIn = getConfig().getInt("patrol.titles.fadeIn", 5);
        titleConf.stay = getConfig().getInt("patrol.titles.stay", 40);
        titleConf.fadeOut = getConfig().getInt("patrol.titles.fadeOut", 10);

        // sounds.onPlayerSpectate
        spectateSoundConf = new SoundConf();
        spectateSoundConf.enabled = getConfig().getBoolean("patrol.sounds.onPlayerSpectate.enabled", false);
        spectateSoundConf.type = getConfig().getString("patrol.sounds.onPlayerSpectate.type",
                "UI_TOAST_CHALLENGE_COMPLETE");
        spectateSoundConf.volume = (float) getConfig().getDouble("patrol.sounds.onPlayerSpectate.volume", 1.0);
        spectateSoundConf.pitch = (float) getConfig().getDouble("patrol.sounds.onPlayerSpectate.pitch", 1.2);

        // tour
        tourConf = new TourConf();
        tourConf.enabled = getConfig().getBoolean("patrol.tour.enabled", true);
        tourConf.dwellSeconds = getConfig().getInt("patrol.tour.dwellSeconds", 10);
        tourConf.file = getConfig().getString("patrol.tour.file", "tourist_locations.yml");
        tourConf.useArmorStandPOIs = getConfig().getBoolean("patrol.tour.useArmorStandPOIs", false);
        tourConf.armorStandTag = getConfig().getString("patrol.tour.armorStandTag", "patrol_poi");
        tourConf.autogenPoints = getConfig().getInt("patrol.tour.autogen.points", 6);
        tourConf.autogenRadius = getConfig().getInt("patrol.tour.autogen.radius", 60);
        tourConf.autogenYOffset = getConfig().getDouble("patrol.tour.autogen.yOffset", 0.0);

        // performance
        performanceConf = new PerformanceConf();
        performanceConf.lowSpecMode = getConfig().getBoolean("performance.lowSpecMode", false);
        performanceConf.minIntervalSeconds = getConfig().getInt("performance.minIntervalSeconds", 60);
        performanceConf.maxCameraMovesPerMinute = getConfig().getInt("performance.maxCameraMovesPerMinute", 3);
        performanceConf.disableWorldScan = getConfig().getBoolean("performance.disableWorldScan", false);
        performanceConf.disableAutoEventWhilePatrol = getConfig().getBoolean("performance.disableAutoEventWhilePatrol",
                false);
        performanceConf.debugLog = getConfig().getBoolean("performance.debugLog", false);
    }

    // —— ここから公共API（他クラスから呼ばれる） ——

    public TitleConf getTitleConf() {
        return titleConf;
    }

    public SoundConf getSpectateSoundConf() {
        return spectateSoundConf;
    }

    public TourConf getTourConf() {
        return tourConf;
    }

    public PerformanceConf getPerformanceConf() {
        return performanceConf;
    }

    public ProtectionData getProtectionData() {
        return protectionData;
    }

    public PlayerStatsStorage getStatsStorage() {
        return statsStorage;
    }

    public ParticipationManager getParticipationManager() {
        return participationManager;
    }

    public PatrolManager getPatrolManager() {
        return patrolManager;
    }

    public EndResetManager getEndResetManager() {
        return endResetManager;
    }

    public EndGameManager getEndGameManager() {
        return endGameManager;
    }

    public int getPatrolIntervalSeconds() {
        return patrolIntervalSeconds;
    }

    // ランキング用：イベントポイント付与（存在しなかったので用意）
    public void addEventPointsToRanking(UUID uuid, int add, String reason) {
        if (uuid == null || statsStorage == null)
            return;
        statsStorage.addEventPoint(uuid, add, reason);
    }

    // 参加者名の保存（存在しなかったので用意）
    public void ensurePlayerNameSaved(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty() || statsStorage == null)
            return;
        statsStorage.ensureName(uuid, name);
    }

    // 死亡保護の延長（存在しなかったので用意）
    public void extendProtectionDuration(UUID uuid, long extraMillis) {
        protectionData.extend(uuid, extraMillis);
    }
}
