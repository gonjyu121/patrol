package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * パトロール（観光巡りおよびプレイヤー観戦）のロジックを管理するクラス。
 * <p>
 * 主な責務:
 * <ul>
 * <li>観光地リスト ({@link TouristLocation}) の管理</li>
 * <li>パトロールタスクの開始・停止・定期実行</li>
 * <li>観光モードとプレイヤー観戦モードの切り替え判断</li>
 * </ul>
 */
public class PatrolManager {

    private final PatrolSpectatorPlugin plugin;
    private final EngagementSystem engagementSystem;
    private final ParticipationManager participationManager;
    private final GameModeEnforcer gameModeEnforcer;
    private final RankingDisplaySystem rankingDisplaySystem;

    // 観光地リスト
    private final List<TouristLocation> touristLocations = new ArrayList<>();

    // 現在の巡回インデックス
    private int currentTourIndex = -1;

    // 現在稼働中の定期タスク
    private BukkitTask patrolTask;

    // カメラ役（/patrol start 実行者）のUUID
    private UUID cameraUuid;
    // パトロール開始時の位置（終了時に戻るため）
    private org.bukkit.Location startLocation;
    // パトロール開始時のインベントリ（終了時に戻すため）
    private org.bukkit.inventory.ItemStack[] savedInventory;
    private org.bukkit.inventory.ItemStack[] savedArmor;

    /**
     * コンストラクタ。
     *
     * @param plugin               プラグインのメインクラス
     * @param engagementSystem     エンゲージメントシステム（観戦対象の選定に使用）
     * @param participationManager 参加管理マネージャー（観戦されたプレイヤーの記録に使用）
     * @param gameModeEnforcer     ゲームモード強制クラス（パトロール終了時のサバイバル復帰に使用）
     * @param rankingDisplaySystem ランキング表示システム（パトロール中のランキング表示に使用）
     */
    public PatrolManager(PatrolSpectatorPlugin plugin,
            EngagementSystem engagementSystem,
            ParticipationManager participationManager,
            GameModeEnforcer gameModeEnforcer,
            RankingDisplaySystem rankingDisplaySystem) {
        this.plugin = plugin;
        this.engagementSystem = engagementSystem;
        this.participationManager = participationManager;
        this.gameModeEnforcer = gameModeEnforcer;
        this.rankingDisplaySystem = rankingDisplaySystem;
    }

    /**
     * 観光地データをロードします。
     * <p>
     * 1. プラグインのデータフォルダ内のYAMLファイルから読み込みを試みます。
     * 2. ファイルが存在しない、または読み込めない場合は config.yml の設定をフォールバックとして使用します。
     */
    public void loadTouristLocations() {
        touristLocations.clear();
        PatrolSpectatorPlugin.TourConf tourConf = plugin.getTourConf();

        // 外部YAML優先
        File f = new File(plugin.getDataFolder(), tourConf.file);
        if (f.exists()) {
            touristLocations.addAll(TouristLocation.loadFromYaml(f));
        }

        // config内のフォールバック
        List<Map<?, ?>> fallback = plugin.getConfig().getMapList("patrol.tour.locations");
        touristLocations.addAll(TouristLocation.fromMapList(fallback));

        plugin.getLogger().info("観光地データをロードしました: " + touristLocations.size() + " 件");
    }

    /**
     * パトロールを開始します。
     * <p>
     * 既にパトロール中の場合は一度停止してから再開します。
     * 観光地リストが空の場合は、設定に応じて自動生成を試みます。
     *
     * @param camera       カメラ役となるプレイヤー
     * @param dwellSeconds 各スポットの滞在時間（秒）
     */
    public void startPatrol(Player camera, int dwellSeconds) {
        stopPatrol(); // 既存タスクがあれば停止

        this.cameraUuid = camera.getUniqueId();
        this.startLocation = camera.getLocation(); // 開始地点を保存
        this.savedInventory = camera.getInventory().getContents(); // インベントリ保存
        this.savedArmor = camera.getInventory().getArmorContents(); // 防具保存

        // GameModeEnforcerの設定と開始
        gameModeEnforcer.setCameraOperator(cameraUuid);
        gameModeEnforcer.start();

        // ランキング表示の開始（カメラ役を除外対象に設定）
        rankingDisplaySystem.setExcludedPlayer(cameraUuid);
        rankingDisplaySystem.startRankingDisplay();

        PatrolSpectatorPlugin.TourConf tourConf = plugin.getTourConf();

        // カメラ役をスペクテイターモードに変更（観光中の事故防止）
        camera.setGameMode(GameMode.SPECTATOR);

        // 観光地リストが空の場合の自動生成処理
        if (touristLocations.isEmpty()) {
            // ワールドが存在する場合のみ自動生成
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world != null) {
                plugin.getLogger().info("観光地リストが空のため、自動生成を試みます。");
                touristLocations.addAll(TouristLocation.autoGenerate(
                        world,
                        tourConf.autogenPoints,
                        tourConf.autogenRadius,
                        tourConf.autogenYOffset));
            }
        }

        // エンドワールドが存在する場合、エンドラ観戦用のポイントを追加（リストに無ければ）
        // 既存のリストにエンドが含まれているかチェック
        boolean hasEnd = touristLocations.stream().anyMatch(l -> l.worldType.equalsIgnoreCase("end"));
        if (!hasEnd) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null) {
                plugin.getLogger().info("エンドワールドが見つかりました。観光地に追加します。");
                // 0, 100, 0 から 0,0,0 を見る
                touristLocations.add(new TouristLocation(
                        "auto_end_01",
                        "§5The End",
                        endWorld.getName(),
                        0.0, 100.0, 0.0,
                        0f, 45f, // yaw=0 (South), pitch=45 (Looking down)
                        "Ender Dragon Arena",
                        "end"));
            }
        }

        currentTourIndex = -1;

        // タスクの実行間隔（tick）を計算。最低でも1秒（20ticks）は確保。
        final int tickPeriod = Math.max(20, dwellSeconds * 20);

        // 定期タスクの開始
        patrolTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tickPatrol();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "パトロール処理(tick)でエラーが発生しました: " + t.getMessage(), t);
            }
        }, 1L, tickPeriod);

        plugin.getLogger().info("パトロールを開始しました。カメラ: " + camera.getName() + ", 間隔: " + dwellSeconds + "秒");
    }

    /**
     * パトロールを停止します。
     * <p>
     * 定期タスクをキャンセルし、カメラ役以外の全プレイヤーをサバイバルモードに戻します。
     * （以前発生した「全員スペクテイター化事故」への安全策）
     */
    public void stopPatrol() {
        if (patrolTask != null) {
            patrolTask.cancel();
            patrolTask = null;
        }

        // GameModeEnforcerの停止
        gameModeEnforcer.clearCameraOperator();
        gameModeEnforcer.stop();

        // ランキング表示の停止（除外設定も解除）
        rankingDisplaySystem.stopRankingDisplay();
        rankingDisplaySystem.setExcludedPlayer(null);

        // 安全策: 全プレイヤーをSurvivalに戻す（カメラ役含む）
        for (Player pl : Bukkit.getOnlinePlayers()) {
            gameModeEnforcer.ensurePlayerIsSurvival(pl);
        }

        // カメラ役を開始地点とインベントリに戻す
        Player camera = getCamera();
        if (camera != null) {
            if (startLocation != null) {
                camera.teleport(startLocation);
            }
            if (savedInventory != null) {
                camera.getInventory().setContents(savedInventory);
            }
            if (savedArmor != null) {
                camera.getInventory().setArmorContents(savedArmor);
            }
        }

        cameraUuid = null;
        startLocation = null;
        savedInventory = null;
        savedArmor = null;
        plugin.getLogger().info("パトロールを停止しました。");
    }

    /**
     * パトロールの状態（実行中かどうか）を返します。
     * 
     * @return 実行中なら true
     */
    public boolean isRunning() {
        return patrolTask != null && !patrolTask.isCancelled();
    }

    /**
     * 現在ロードされている観光地の数を返します。
     * 
     * @return 観光地数
     */
    public int getLocationCount() {
        return touristLocations.size();
    }

    /**
     * 定期実行されるパトロール処理の本体。
     * <p>
     * 1. カメラ役プレイヤーの有効性チェック
     * 2. 近くに「映すべきプレイヤー（ターゲット）」がいるか確認
     * 3. ターゲットがいればそのプレイヤーを観戦（スペクテイター）
     * 4. いなければ次の観光地へテレポート
     */
    private void tickPatrol() {
        Player camera = getCamera();
        if (camera == null || !camera.isOnline()) {
            // カメラ役がオフラインになった場合などは何もしない（あるいは停止すべき？）
            return;
        }

        // 1. 近くに「視点奪取対象」が居ればそっち優先
        // 検索半径は 48.0 ブロック
        Player target = engagementSystem.findGoodTargetNear(camera, 48.0);

        if (target != null) {
            // ターゲットが見つかった場合: プレイヤー観戦モード
            spectateTarget(camera, target);

            // 参加（映ったこと）を記録
            participationManager.noteParticipation(target.getUniqueId(), target.getName());

            // タイトル表示：プレイヤー名を大きく強調
            MessageUtils.showTourTitle(camera, "§b" + target.getName() + " §7さんの視点");
            return;
        }

        // 2. ターゲットがいなければ観光巡り：次のスポットへ
        // 異世界（ネザー・エンド）にいる場合は、一度オーバーワールドに戻る（描画バグ防止）
        if (camera.getWorld().getEnvironment() != World.Environment.NORMAL) {
            World mainWorld = Bukkit.getWorlds().get(0);
            if (mainWorld != null) {
                camera.teleport(mainWorld.getSpawnLocation());
                MessageUtils.showTourTitle(camera, "§7帰還中...");
                return; // 次のtickで観光地へ
            }
        }

        if (touristLocations.isEmpty())
            return;

        // インデックスを進める（ループする）
        currentTourIndex = (currentTourIndex + 1) % touristLocations.size();
        TouristLocation tl = touristLocations.get(currentTourIndex);

        World w = Bukkit.getWorld(tl.world);
        if (w == null) {
            // ワールドが見つからない場合はスキップ（ログを出してもいいかも）
            return;
        }

        // エンドリセット中はエンドワールドをスキップ
        if (plugin.getEndResetManager() != null && plugin.getEndResetManager().isResetting()) {
            if (w.getName().equals("world_the_end") || w.getEnvironment() == World.Environment.THE_END) {
                return;
            }
        }

        // pitch が極端（真下/真上）になりすぎないよう補正：±85度にクリップ
        float safePitch = Math.max(-85f, Math.min(85f, tl.pitch));

        // テレポート実行
        org.bukkit.Location loc = new org.bukkit.Location(w, tl.x, tl.y, tl.z, tl.yaw, safePitch);
        camera.teleport(loc);

        MessageUtils.showTourTitle(camera, tl.name);
    }

    /**
     * カメラ役のプレイヤーを取得します。
     * 
     * @return カメラ役プレイヤー、設定されていない場合は null
     */
    private Player getCamera() {
        if (cameraUuid == null)
            return null;
        return Bukkit.getPlayer(cameraUuid);
    }

    /**
     * 指定したターゲットプレイヤーを観戦（スペクテイター）します。
     *
     * @param camera カメラ役プレイヤー
     * @param target 観戦対象プレイヤー
     */
    private void spectateTarget(Player camera, Player target) {
        if (camera == null || target == null)
            return;
        try {
            camera.setGameMode(GameMode.SPECTATOR);
            // PaperAPI: SpectatorTarget を設定
            camera.setSpectatorTarget(target);
        } catch (Throwable t) {
            // Paper API非対応環境などのためのフォールバック：テレポートで追従
            camera.teleport(target.getLocation());
        }

        // 観戦開始時のサウンド再生（設定で有効な場合）
        PatrolSpectatorPlugin.SoundConf soundConf = plugin.getSpectateSoundConf();
        if (soundConf != null && soundConf.enabled) {
            try {
                engagementSystem.playNamedSound(camera, soundConf.type, soundConf.volume, soundConf.pitch);
            } catch (Throwable ignored) {
            }
        }
    }
}
