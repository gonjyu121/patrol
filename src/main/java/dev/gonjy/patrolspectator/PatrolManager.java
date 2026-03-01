package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

    // 直前に観戦していたプレイヤーのUUID（ローテーション用）
    private UUID lastSpectatedUuid;

    // 最後にサマリログを出力した時刻
    private long lastSummaryLogTime = 0;

    // 事前読み込み用タスク
    private BukkitTask preLoadTask;

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
        prepareTour();
    }

    /**
     * ツアーの準備（自動生成や初期データの補完）を行います。
     * startup時に1回だけ実行される。
     */
    public void prepareTour() {
        PatrolSpectatorPlugin.TourConf tourConf = plugin.getTourConf();

        // 観光地リストが空の場合の自動生成処理
        if (touristLocations.isEmpty()) {
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

        // エンド追加
        boolean hasEnd = touristLocations.stream()
                .anyMatch(l -> l.worldType != null && l.worldType.equalsIgnoreCase("end"));
        if (!hasEnd) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null) {
                plugin.getLogger().info("エンドワールドが見つかりました。観光地に追加します。");
                touristLocations.add(new TouristLocation(
                        "auto_end_01",
                        "§5The End",
                        endWorld.getName(),
                        0.0, 100.0, 0.0,
                        0f, 0f,
                        "Ender Dragon Arena",
                        "end"));
            }
        }

        // 死の迷宮追加
        boolean hasDungeon = touristLocations.stream()
                .anyMatch(l -> "auto_dungeon_entrance".equals(l.id));
        if (!hasDungeon) {
            dev.gonjy.patrolspectator.dungeon.DungeonManager dungeon = plugin.getDungeonManager();
            if (dungeon != null && dungeon.isEnabled()) {
                org.bukkit.Location center = dungeon.getCenter();
                if (center != null) {
                    plugin.getLogger().info("死の迷宮が見つかりました。観光地に追加します。");
                    // 入口が見える位置に配置（北側に少し離れて、やや高い視点）
                    touristLocations.add(new TouristLocation(
                            "auto_dungeon_entrance",
                            "§4死の迷宮 - 入口",
                            center.getWorld().getName(),
                            center.getX(), center.getY() + 4.0, center.getZ() - 8.0,
                            0f, 20f,
                            "Death Dungeon Entrance",
                            "overworld"));
                }
            }
        }
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

        // 注: 重い初期化（applyServerRulesやprepareTour）はonEnableで行うよう変更

        // ランキング表示の開始
        rankingDisplaySystem.setExcludedPlayer(cameraUuid);
        rankingDisplaySystem.startRankingDisplay();

        // カメラ役をスペクテイターモードに変更
        camera.setGameMode(GameMode.SPECTATOR);

        // 低スペックモード：最小間隔を強制
        PatrolSpectatorPlugin.PerformanceConf perf = plugin.getPerformanceConf();
        if (perf.lowSpecMode && dwellSeconds < perf.minIntervalSeconds) {
            dwellSeconds = perf.minIntervalSeconds;
            plugin.getLogger().info("[Performance] 巡回間隔を最小制限の " + dwellSeconds + "秒に設定しました。");
        }

        // 低スペックモード：パトロール開始直後にイベントが始まらないようにタイマーリセット
        if (perf.lowSpecMode && perf.disableAutoEventWhilePatrol) {
            plugin.getAutoEventSystem().resetLastEventTime();
        }

        // 次の巡回インデックスのリセット
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

        // 初回の事前読み込みをスケジュール
        schedulePreLoad(dwellSeconds);

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

        if (preLoadTask != null) {
            preLoadTask.cancel();
            preLoadTask = null;
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
            // camera.setReducedDebugInfo(false); // IDE error workaround
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
        lastSpectatedUuid = null;
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

        // 定期的なサマリログ（5分おき）
        long now = System.currentTimeMillis();
        if (now - lastSummaryLogTime > 5 * 60 * 1000L) {
            lastSummaryLogTime = now;
            int onlineTotal = Bukkit.getOnlinePlayers().size();
            plugin.getLogger()
                    .info("[Patrol Summary] Running. Camera: " + camera.getName() + ", Online: " + onlineTotal);
        }

        // 1. 有効なターゲット一覧を取得
        java.util.List<Player> validTargets = engagementSystem.getValidTargets(camera);
        Player target = null;

        if (!validTargets.isEmpty()) {
            // ターゲット候補が複数いる場合、直前に観戦していたプレイヤーを除外してローテーションさせる
            if (validTargets.size() > 1 && lastSpectatedUuid != null) {
                // リストをコピーして操作（元のリストは変更しない）
                java.util.List<Player> candidates = new java.util.ArrayList<>(validTargets);
                candidates.removeIf(p -> p.getUniqueId().equals(lastSpectatedUuid));

                if (!candidates.isEmpty()) {
                    // 候補からランダムに選択
                    target = candidates.get(new java.util.Random().nextInt(candidates.size()));
                }
            }

            // まだ決まっていない場合（候補が一人しかいない、または全員除外された場合など）
            if (target == null) {
                // 近くにいるプレイヤーを優先（観光モードからの切り替え時など）
                target = engagementSystem.findGoodTargetNear(camera, 48.0);

                // それでも決まらなければ、リストからランダムに選択
                if (target == null) {
                    target = validTargets.get(new java.util.Random().nextInt(validTargets.size()));
                }
            }

            if (plugin.getPerformanceConf().debugLog) {
                StringBuilder sb = new StringBuilder("§a[Patrol] 現在、以下のプレイヤーがプレイ中です: ");
                for (int i = 0; i < validTargets.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(validTargets.get(i).getName());
                }
                camera.sendMessage(sb.toString());
            }
        }

        // 次のサイクルに向けた事前読み込みをスケジュール
        schedulePreLoad(plugin.getTourConf().dwellSeconds);

        if (target != null) {
            // ターゲットが見つかった場合: プレイヤー観戦モード
            spectateTarget(camera, target);

            // 参加（映ったこと）を記録
            participationManager.noteParticipation(target.getUniqueId(), target.getName());

            // タイトル表示：プレイヤー名を大きく強調
            MessageUtils.showTitleLargeSmall(camera, "§b" + target.getName() + " §7さんの視点", "§aNow On Air");
            return;
        }

        // ターゲットが見つからなかった場合、観光巡り（Tour）セクションへ進む
        // 低スペックモードでも、誰もいないときは観光地を映すように修正

        // 2. ターゲットがいなければ観光巡り：次のスポットへ
        if (touristLocations.isEmpty())
            return;

        // 次の有効な観光地を探す（最大でリストサイズ分だけ試行）
        TouristLocation nextLocation = null;
        int attempts = 0;
        int listSize = touristLocations.size();

        while (attempts < listSize) {
            // インデックスを進める（ループする）
            currentTourIndex = (currentTourIndex + 1) % listSize;
            TouristLocation candidate = touristLocations.get(currentTourIndex);

            World w = Bukkit.getWorld(candidate.world);
            if (w == null) {
                // ワールドが見つからない場合はスキップ
                attempts++;
                continue;
            }

            // エンドリセット中、またはエンダードラゴンがいない場合の処理
            if (w.getName().equals("world_the_end") || w.getEnvironment() == World.Environment.THE_END) {
                if (plugin.getEndResetManager() != null) {
                    // リセット処理中(ワールド削除中など)はスキップ
                    if (plugin.getEndResetManager().isResetting()) {
                        // plugin.getLogger().info("[Debug] Skipping End world because it is
                        // resetting.");
                        attempts++;
                        continue;
                    }
                }
            }

            // 有効な場所が見つかった
            nextLocation = candidate;
            break;
        }

        if (nextLocation == null) {
            // 有効な観光地が一つもない場合
            return;
        }

        World w = Bukkit.getWorld(nextLocation.world);
        if (w == null) {
            // 低スペックモード時は強制ロードを行わない(フリーズ防止)
            // ただし、エンドワールド(world_the_end)だけは特別に許可する
            if (plugin.getPerformanceConf().lowSpecMode && !nextLocation.world.equalsIgnoreCase("world_the_end")) {
                return;
            }
            try {
                w = Bukkit.createWorld(new org.bukkit.WorldCreator(nextLocation.world));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load world: " + nextLocation.world);
            }
        }

        if (w == null) {
            plugin.getLogger().warning("[Debug] Skipped patrol location " + nextLocation.name + " because world "
                    + nextLocation.world + " could not be loaded.");
            return;
        }

        // *** 特殊ロジック: エンドならドラゴンを探す ***
        // v1.9.53: disableWorldScanがtrueでも、エンドに入った時だけは特別にドラゴンを探す
        if (w.getEnvironment() == World.Environment.THE_END) {
            // v1.9.54+: getEntitiesByClass を使用して全エンティティ走査を回避
            var dragons = w.getEntitiesByClass(org.bukkit.entity.EnderDragon.class);
            org.bukkit.entity.EnderDragon dragon = dragons.isEmpty() ? null : dragons.iterator().next();

            if (dragon != null && dragon.isValid()) {
                spectateEntity(camera, dragon);

                // タイトル表示
                MessageUtils.showTitleLargeSmall(camera, "§5The Void Dragon", "§7Now On Air");
                return;
            }
        }

        // pitch が極端（真下/真上）になりすぎないよう補正：±85度にクリップ
        float safePitch = Math.max(-85f, Math.min(85f, nextLocation.pitch));

        // テレポート実行
        org.bukkit.Location loc = new org.bukkit.Location(w, nextLocation.x, nextLocation.y, nextLocation.z,
                nextLocation.yaw, safePitch);
        camera.teleport(loc);
        camera.setFlying(true); // スペクテイターモードでの重力落下（視点ズレ）防止

        // タイトル表示（エンドリセット待機中なら残り時間を追記）
        String subTitle = "";
        if (w.getEnvironment() == World.Environment.THE_END && plugin.getEndResetManager() != null) {
            long remaining = plugin.getEndResetManager().getRemainingResetTimeMillis();
            if (remaining > 0) {
                long mins = remaining / 60000;
                long secs = (remaining % 60000) / 1000;
                subTitle = "§c再生成まで: " + mins + "分" + secs + "秒";
            }
        }

        if (!subTitle.isEmpty()) {
            MessageUtils.showTitleLargeSmall(camera, nextLocation.name, subTitle);
        } else {
            MessageUtils.showTourTitle(camera, nextLocation.name);
        }
    }

    /**
     * カメラ役のプレイヤーを取得します。
     * 
     * @return カメラ役プレイヤー、設定されていない場合は null
     */
    public Player getCameraPlayer() {
        if (cameraUuid == null)
            return null;
        return Bukkit.getPlayer(cameraUuid);
    }

    private Player getCamera() {
        return getCameraPlayer();
    }

    /**
     * 指定したターゲットプレイヤーを観戦（スペクテイター）します。
     *
     * @param camera カメラ役プレイヤー
     * @param target 観戦対象プレイヤー
     */
    private void spectateTarget(Player camera, Player target) {
        spectateEntity(camera, target);
    }

    /**
     * 指定したエンティティを観戦（スペクテイター）します。
     *
     * @param camera カメラ役プレイヤー
     * @param target 観戦対象エンティティ
     */
    private void spectateEntity(Player camera, org.bukkit.entity.Entity target) {
        if (camera == null || target == null)
            return;

        // プレイヤーの場合は記録更新（ローテーション用）
        if (target instanceof Player) {
            lastSpectatedUuid = target.getUniqueId();
        }

        // 3. まず同じ場所にテレポートさせる（ワールド読み込み・チャンク同期のため）
        // 低スペックモード：移動が多すぎないよう最低限の遅延を入れる
        Location targetLoc = target.getLocation();
        if (camera.getWorld().equals(targetLoc.getWorld())) {
            camera.teleport(targetLoc);
        } else {
            // ワールドが異なる場合は1tick遅延実行（Paper/Magma負荷対策）
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (camera.isOnline() && target.isValid()) {
                    camera.teleport(target.getLocation());
                }
            }, 1L);
        }

        // 4. 少し待ってから視点を奪う（クライアントのロード待ち・レースコンディション回避）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!camera.isOnline() || !target.isValid())
                return;

            try {
                // 低スペックモード：状態が変わった時のみ切替
                if (camera.getGameMode() != GameMode.SPECTATOR) {
                    camera.setGameMode(GameMode.SPECTATOR);
                }
                // PaperAPI: SpectatorTarget を設定
                camera.setSpectatorTarget(target);
            } catch (Throwable t) {
                // Paper API非対応環境などのためのフォールバック：再度テレポートで追従
                camera.teleport(target.getLocation());
            }

            // プレイヤーの場合のみサウンド再生
            if (target instanceof Player) {
                PatrolSpectatorPlugin.SoundConf soundConf = plugin.getSpectateSoundConf();
                if (soundConf != null && soundConf.enabled) {
                    try {
                        engagementSystem.playNamedSound(camera, soundConf.type, soundConf.volume, soundConf.pitch);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 10L); // 10 ticks delay (0.5s)
    }

    /**
     * 次の巡回予定地のチャンクを非同期で事前読み込みします。
     */
    private void schedulePreLoad(int dwellSeconds) {
        if (preLoadTask != null) {
            preLoadTask.cancel();
        }

        PatrolSpectatorPlugin.ChunkPreLoadingConf chunkConf = plugin.getChunkPreLoadingConf();
        if (!chunkConf.enabled)
            return;

        long delayTicks = (long) (dwellSeconds - chunkConf.secondsBefore) * 20L;
        if (delayTicks < 0)
            delayTicks = 1L;

        preLoadTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            preLoadNextChunks();
        }, delayTicks);
    }

    private void preLoadNextChunks() {
        // 次のターゲット（プレイヤーまたは観光地）を予測
        Player camera = getCamera();
        if (camera == null)
            return;

        List<Player> validTargets = engagementSystem.getValidTargets(camera);
        if (!validTargets.isEmpty()) {
            // プレイヤー観戦が優先される可能性が高い
            for (Player p : validTargets) {
                loadChunksAround(p.getLocation());
            }
        } else if (!touristLocations.isEmpty()) {
            // 観光地巡りになる可能性が高い
            int nextIndex = (currentTourIndex + 1) % touristLocations.size();
            TouristLocation nextLoc = touristLocations.get(nextIndex);
            World w = Bukkit.getWorld(nextLoc.world);
            if (w != null) {
                loadChunksAround(new Location(w, nextLoc.x, nextLoc.y, nextLoc.z));
            }
        }
    }

    private void loadChunksAround(Location loc) {
        if (loc == null || loc.getWorld() == null)
            return;

        World world = loc.getWorld();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        // 半径1チャンク分を非同期ロード（負荷を抑えつつ準備）
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getChunkAtAsync(chunkX + x, chunkZ + z);
            }
        }
    }
}
