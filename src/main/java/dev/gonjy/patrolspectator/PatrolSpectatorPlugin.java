package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public class PatrolSpectatorPlugin extends JavaPlugin {

    // サブモジュール
    private AutoEventSystem autoEventSystem;
    private EngagementSystem engagementSystem;
    private GameModeEnforcer gameModeEnforcer;
    private ParticipationManager participationManager;
    private PatrolManager patrolManager;
    private RankingDisplaySystem rankingDisplaySystem;

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

    private TourConf tourConf;
    private int patrolIntervalSeconds;
    private boolean announce; // 予約（未使用だが残す）

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
        engagementSystem = new EngagementSystem(this);
        gameModeEnforcer = new GameModeEnforcer(this);
        autoEventSystem = new AutoEventSystem(this);
        participationManager = new ParticipationManager(this, statsStorage);
        rankingDisplaySystem = new RankingDisplaySystem(this, statsStorage);

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new RankingEventListener(statsStorage), this);

        // PatrolManagerの初期化（依存関係を注入）
        patrolManager = new PatrolManager(this, engagementSystem, participationManager, gameModeEnforcer,
                rankingDisplaySystem);

        // ルール適用（Bedrock系 gamerule は失敗するので握りつぶす）
        applyServerRulesSafely();

        // 観光地ロード
        patrolManager.loadTouristLocations();

        getLogger().info("PatrolSpectatorPlugin enabled.");
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

        // 最後にストレージ保存
        if (statsStorage != null) {
            statsStorage.flush();
        }
        getLogger().info("PatrolSpectatorPlugin disabled.");
    }

    private void loadConfigValues() {
        reloadConfig();
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
    }

    private void applyServerRulesSafely() {
        // Paper(Java)で通る範囲のみ実行。Bedrock専用は例外になるので握りつぶす
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        String[] cmds = new String[] {
                "gamerule doDaylightCycle true",
                "gamerule keepInventory false",
                "gamerule locatorBar false", // Bedrock寄りAPIだが、実行できる環境もあるのでtry
                "gamerule showCoordinates false" // ほぼ失敗するので try/catchで握る
        };
        for (String c : cmds) {
            try {
                Bukkit.dispatchCommand(console, c);
                getLogger().info("[Rules] applied: " + c);
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "[Rules] failed: " + c + " (" + t.getMessage() + ")");
            }
        }
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

    // 観光タイトル表示（名称を大きく／「観光地」は小さく）
    public void showTourTitle(Player p, String name) {
        if (p == null || !titleConf.enabled)
            return;
        try {
            // 上段を名称（大）、下段を「観光地」
            p.sendTitle("§l" + name, "§7観光地", titleConf.fadeIn, titleConf.stay, titleConf.fadeOut);
        } catch (Throwable ignored) {
        }
    }

    /**
     * タイトルを大きく（上段）と小さく（下段）で表示します。
     * 
     * @param p        プレイヤー
     * @param title    上段のタイトル
     * @param subtitle 下段のサブタイトル
     */
    public void showTitleLargeSmall(Player p, String title, String subtitle) {
        if (p == null || !titleConf.enabled)
            return;
        try {
            p.sendTitle(title, subtitle, titleConf.fadeIn, titleConf.stay, titleConf.fadeOut);
        } catch (Throwable ignored) {
        }
    }

    /**
     * カラーコード付きの太字テキストを生成します。
     * 
     * @param hexColor 16進数カラーコード（例: "#A5D6A7"）
     * @param text     テキスト内容
     * @return フォーマット済みテキスト
     */
    public String textBold(String hexColor, String text) {
        return "§l" + hexColor + text;
    }

    /**
     * カラーコード付きのテキストを生成します。
     * 
     * @param hexColor 16進数カラーコード（例: "#FFFFFF"）
     * @param text     テキスト内容
     * @return フォーマット済みテキスト
     */
    public String text(String hexColor, String text) {
        return hexColor + text;
    }

    // —— /patrol コマンド ——
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"patrol".equalsIgnoreCase(command.getName()))
            return false;

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§a/patrol start [dwellSeconds] - 観光巡りをスタート");
            sender.sendMessage("§a/patrol stop                 - 停止");
            sender.sendMessage("§a/patrol status               - 状態表示");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;

                int dwell = tourConf.dwellSeconds;
                if (args.length >= 2) {
                    try {
                        dwell = Math.max(3, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                patrolManager.startPatrol(p, dwell);
                sender.sendMessage("§a[Patrol] start (dwell=" + dwell + "s)");
                break;
            }
            case "stop": {
                patrolManager.stopPatrol();
                sender.sendMessage("§e[Patrol] stop");
                break;
            }
            case "status": {
                String running = patrolManager.isRunning() ? "RUNNING" : "IDLE";
                sender.sendMessage("§b[Patrol] status=" + running + ", locations=" + patrolManager.getLocationCount());
                break;
            }
            default:
                sender.sendMessage("Unknown subcommand. /patrol help");
        }
        return true;
    }
}
