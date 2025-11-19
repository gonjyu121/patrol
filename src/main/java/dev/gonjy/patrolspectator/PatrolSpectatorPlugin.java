package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.OldEnum;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class PatrolSpectatorPlugin extends JavaPlugin {

    // サブモジュール
    private AutoEventSystem autoEventSystem;
    private EngagementSystem engagementSystem;
    private GameModeEnforcer gameModeEnforcer;
    private ParticipationManager participationManager;

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

    // 現在稼働中の定期タスク
    private BukkitTask patrolTask;

    // カメラ（/patrol start 実行者）
    private UUID cameraUuid;

    // 観光地リスト
    private List<TouristLocation> touristLocations = new ArrayList<>();
    private int currentTourIndex = -1;

    // 保護情報
    private final ProtectionData protectionData = new ProtectionData();

    // 参加回数・ランキング
    private PlayerStatsStorage statsStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        // ストレージ
        statsStorage = new PlayerStatsStorage(this);

        // サブシステム
        engagementSystem = new EngagementSystem(this);
        gameModeEnforcer = new GameModeEnforcer(this);
        autoEventSystem = new AutoEventSystem(this);
        participationManager = new ParticipationManager(this, statsStorage);

        // ルール適用（Bedrock系 gamerule は失敗するので握りつぶす）
        applyServerRulesSafely();

        // 観光地ロード
        loadTouristLocations();

        getLogger().info("PatrolSpectatorPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (patrolTask != null) {
            patrolTask.cancel();
            patrolTask = null;
        }
        if (autoEventSystem != null) autoEventSystem.shutdown();
        if (gameModeEnforcer != null) gameModeEnforcer.shutdown();

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
        titleConf.stay   = getConfig().getInt("patrol.titles.stay", 40);
        titleConf.fadeOut= getConfig().getInt("patrol.titles.fadeOut", 10);

        // sounds.onPlayerSpectate
        spectateSoundConf = new SoundConf();
        spectateSoundConf.enabled = getConfig().getBoolean("patrol.sounds.onPlayerSpectate.enabled", false);
        spectateSoundConf.type    = getConfig().getString("patrol.sounds.onPlayerSpectate.type", "UI_TOAST_CHALLENGE_COMPLETE");
        spectateSoundConf.volume  = (float) getConfig().getDouble("patrol.sounds.onPlayerSpectate.volume", 1.0);
        spectateSoundConf.pitch   = (float) getConfig().getDouble("patrol.sounds.onPlayerSpectate.pitch", 1.2);

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
        String[] cmds = new String[]{
                "gamerule doDaylightCycle true",
                "gamerule keepInventory false",
                "gamerule locatorBar false",      // Bedrock寄りAPIだが、実行できる環境もあるのでtry
                "gamerule showCoordinates false"  // ほぼ失敗するので try/catchで握る
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

    private void loadTouristLocations() {
        touristLocations.clear();
        // 外部YAML優先
        File f = new File(getDataFolder(), tourConf.file);
        if (f.exists()) {
            touristLocations.addAll(TouristLocation.loadFromYaml(f));
        }
        // config内のフォールバック
        List<Map<?,?>> fallback = getConfig().getMapList("patrol.tour.locations");
        touristLocations.addAll(TouristLocation.fromMapList(fallback));
        getLogger().info("Loaded tourist locations: " + touristLocations.size());
    }

    // —— ここから公共API（他クラスから呼ばれる） ——

    public TitleConf getTitleConf() { return titleConf; }
    public SoundConf getSpectateSoundConf() { return spectateSoundConf; }
    public TourConf getTourConf() { return tourConf; }

    public ProtectionData getProtectionData() { return protectionData; }
    public PlayerStatsStorage getStatsStorage() { return statsStorage; }
    public ParticipationManager getParticipationManager() { return participationManager; }

    public int getPatrolIntervalSeconds() { return patrolIntervalSeconds; }

    public List<TouristLocation> getTouristLocations() { return touristLocations; }
    public int getCurrentTourIndex() { return currentTourIndex; }
    public void setCurrentTourIndex(int idx) { currentTourIndex = idx; }

    public UUID getCameraUuid() { return cameraUuid; }

    // ランキング用：イベントポイント付与（存在しなかったので用意）
    public void addEventPointsToRanking(UUID uuid, int add, String reason) {
        if (uuid == null || statsStorage == null) return;
        statsStorage.addEventPoint(uuid, add, reason);
    }

    // 参加者名の保存（存在しなかったので用意）
    public void ensurePlayerNameSaved(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty() || statsStorage == null) return;
        statsStorage.ensureName(uuid, name);
    }

    // 死亡保護の延長（存在しなかったので用意）
    public void extendProtectionDuration(UUID uuid, long extraMillis) {
        protectionData.extend(uuid, extraMillis);
    }

    // カメラを取得（nullの場合あり）
    public Player getCamera() {
        if (cameraUuid == null) return null;
        return Bukkit.getPlayer(cameraUuid);
    }

    // 観光タイトル表示（名称を大きく／「観光地」は小さく）
    public void showTourTitle(Player p, String name) {
        if (p == null || !titleConf.enabled) return;
        try {
            // 上段を名称（大）、下段を「観光地」
            p.sendTitle("§l" + name, "§7観光地", titleConf.fadeIn, titleConf.stay, titleConf.fadeOut);
        } catch (Throwable ignored) {}
    }

    // 視点奪取（Paperの spectator target を優先、失敗時はTPフォールバック）
    public void spectateTarget(Player camera, Player target) {
        if (camera == null || target == null) return;
        try {
            camera.setGameMode(GameMode.SPECTATOR);
            // PaperAPI: SpectatorTarget
            camera.setSpectatorTarget(target);
        } catch (Throwable t) {
            // だめな環境では軽いTPフォールバック
            camera.teleport(target.getLocation());
        }
        // サウンド（必要なら）
        if (spectateSoundConf.enabled) {
            try {
                engagementSystem.playNamedSound(camera, spectateSoundConf.type, spectateSoundConf.volume, spectateSoundConf.pitch);
            } catch (Throwable ignored) {}
        }
    }

    // —— /patrol コマンド ——
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"patrol".equalsIgnoreCase(command.getName())) return false;

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
                this.cameraUuid = p.getUniqueId();

                int dwell = tourConf.dwellSeconds;
                if (args.length >= 2) {
                    try { dwell = Math.max(3, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
                }

                startPatrol(dwell);
                sender.sendMessage("§a[Patrol] start (dwell=" + dwell + "s)");
                break;
            }
            case "stop": {
                stopPatrol();
                sender.sendMessage("§e[Patrol] stop");
                break;
            }
            case "status": {
                String running = (patrolTask != null && !patrolTask.isCancelled()) ? "RUNNING" : "IDLE";
                sender.sendMessage("§b[Patrol] status=" + running + ", locations=" + touristLocations.size());
                break;
            }
            default:
                sender.sendMessage("Unknown subcommand. /patrol help");
        }
        return true;
    }

    private void startPatrol(int dwellSeconds) {
        stopPatrol();

        // index 初期化
        if (touristLocations.isEmpty()) {
            // 自動生成もやっておく（必要な場合）
            touristLocations.addAll(TouristLocation.autoGenerate(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0),
                    tourConf.autogenPoints, tourConf.autogenRadius, tourConf.autogenYOffset));
        }
        currentTourIndex = -1;

        final int tickPeriod = Math.max(20, dwellSeconds * 20);
        patrolTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                tickPatrol();
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "patrol tick failed: " + t.getMessage(), t);
            }
        }, 1L, tickPeriod);
    }

    private void stopPatrol() {
        if (patrolTask != null) {
            patrolTask.cancel();
            patrolTask = null;
        }
        // カメラ以外はSurvivalに戻す保険（以前の「全員SP化」事故対策）
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (cameraUuid != null && cameraUuid.equals(pl.getUniqueId())) continue;
            gameModeEnforcer.ensurePlayerIsSurvival(pl);
        }
    }

    private void tickPatrol() {
        Player camera = getCamera();
        if (camera == null || !camera.isOnline()) return;

        // 近くに「視点奪取対象」が居ればそっち優先
        Player target = engagementSystem.findGoodTargetNear(camera, 48.0);
        if (target != null) {
            spectateTarget(camera, target);
            participationManager.noteParticipation(target.getUniqueId(), target.getName());
            // タイトル：プレイヤー名を大きく
            showTourTitle(camera, "§b" + target.getName() + " §7さんの視点");
            return;
        }

        // いなければ観光巡り：次のスポットへ
        if (touristLocations.isEmpty()) return;
        currentTourIndex = (currentTourIndex + 1) % touristLocations.size();
        TouristLocation tl = touristLocations.get(currentTourIndex);

        World w = Bukkit.getWorld(tl.world);
        if (w == null) return;

        // pitch が極端（真下）なら補正：±85度にクリップ
        float safePitch = Math.max(-85f, Math.min(85f, tl.pitch));
        Ticks.teleportWithYawPitch(camera, w, tl.x, tl.y, tl.z, tl.yaw, safePitch);

        // 名称を大きく、下段に「観光地」
        showTourTitle(camera, tl.name);
    }

    // —— 軽いユーティリティ ——（OldEnum.name() の deprecation 警告対策）
    @SuppressWarnings("removal")
    private static String safeOldEnumName(OldEnum e) {
        return (e == null) ? "" : e.name(); // Paperの将来削除予定API。現状は抑制で対処
    }
}
