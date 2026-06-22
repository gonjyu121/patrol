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
public class PatrolManager implements org.bukkit.event.Listener {

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
    private int currentDwellSeconds = 8;

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

    // シネマティック追跡用タスク（エンドラ等で使用）
    private BukkitTask trackingTask;
    private org.bukkit.entity.ArmorStand cinematicCameraStand;

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

        File f = new File(plugin.getDataFolder(), tourConf.file);
        boolean fileExists = f.exists();

        if (fileExists) {
            List<TouristLocation> loaded = TouristLocation.loadFromYaml(f);
            touristLocations.addAll(loaded);
            plugin.getLogger().info("[Patrol] " + tourConf.file + " から " + loaded.size() + " 件の観光地を読み込みました。");
        } else {
            plugin.getLogger().warning("[Patrol] 観光地設定ファイルが見つかりません。初期スポットの自動生成を行います。");
        }

        // config内のフォールバック
        List<Map<?, ?>> fallback = plugin.getConfig().getMapList("patrol.tour.locations");
        if (fallback != null && !fallback.isEmpty()) {
            List<TouristLocation> fallbackLoaded = TouristLocation.fromMapList(fallback);
            touristLocations.addAll(fallbackLoaded);
            plugin.getLogger().info("[Patrol] config.yml から " + fallbackLoaded.size() + " 件の観光地を読み込みました（フォールバック）。");
        }

        prepareTour();

        if (!fileExists && !touristLocations.isEmpty()) {
            TouristLocation.saveToYaml(f, touristLocations);
            plugin.getLogger().info("[Patrol] 自動生成された初期の観光地リストを " + tourConf.file + " に保存しました！");
        }

        plugin.getLogger().info("[Patrol] 最終的な観光地リスト: " + touristLocations.size() + " 件");
    }

    /**
     * ツアーの準備（自動生成や初期データの補完）を行います。
     * startup時に1回だけ実行される。
     */
    public void prepareTour() {
        PatrolSpectatorPlugin.TourConf tourConf = plugin.getTourConf();

        if (touristLocations.isEmpty()) {
            plugin.getLogger().info("観光地リストが空のため、初期拠点等を自動生成します。");
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world != null) {
                org.bukkit.Location spawn = world.getSpawnLocation();
                double y = Math.max(spawn.getY(), world.getHighestBlockYAt(spawn) + 2.0);
                touristLocations.add(new TouristLocation(
                        "auto_spawn",
                        "§a初期リスポーン地点",
                        world.getName(),
                        spawn.getX(), y, spawn.getZ(),
                        0f, 20f,
                        "Server Spawn Point",
                        "overworld",
                        null, null
                ));
            }

            World nether = Bukkit.getWorld("world_nether");
            if (nether == null) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NETHER) {
                        nether = w;
                        break;
                    }
                }
            }
            if (nether != null) {
                touristLocations.add(new TouristLocation(
                        "auto_nether",
                        "§cNether",
                        nether.getName(),
                        0.0, 64.0, 0.0,
                        0f, 0f,
                        "Nether Initial Point",
                        "nether",
                        null, null
                ));
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
                        "end",
                        null, null));
            }
        }

        // 死の迷宮追加
        boolean hasDungeon = touristLocations.stream()
                .anyMatch(l -> "auto_dungeon_entrance".equals(l.id));
        if (!hasDungeon) {
            addDungeonLocations(plugin.getDungeonManager());
        }
    }

    /**
     * 強制的にダンジョンの場所（入口）を（再）登録します。
     * 重複を避けるため、既存の auto_dungeon_entrance は削除します。
     */
    public void addDungeonLocations(dev.gonjy.patrolspectator.dungeon.DungeonManager dungeon) {
        if (dungeon == null || !dungeon.isEnabled()) return;
        org.bukkit.Location center = dungeon.getCenter();
        if (center == null) return;

        // 既存のダンジョン入口を削除
        touristLocations.removeIf(l -> "auto_dungeon_entrance".equals(l.id));

        // 入口を追加
        touristLocations.add(new TouristLocation(
                "auto_dungeon_entrance",
                "§4死の迷宮 - 入口",
                center.getWorld().getName(),
                center.getX(), center.getY() + 4.0, center.getZ() - 8.0,
                0f, 20f,
                "Death Dungeon Entrance",
                "overworld",
                null, null));

        plugin.getLogger().info("[Patrol] 観光案内リストに死の迷宮の入口を登録しました。");
    }

    /**
     * 動的に観光地を追加します（作成完了時に呼ばれる想定）。
     * 同じIDがあれば上書きします。
     */
    public void addTouristLocation(TouristLocation loc) {
        if (loc != null) {
            touristLocations.removeIf(l -> l.id.equals(loc.id));
            touristLocations.add(loc);
            plugin.getLogger().info("[Patrol] 観光案内リストに " + loc.name + " (" + loc.id + ") を追加しました。");
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
        if (plugin.getTickMonitor() != null) {
            plugin.getTickMonitor().resetPauseState();
        }

        // 開始地点を先に保存（stopPatrol()のnullクリアより前に保持する）
        // startLocation が外部から setStartLocation() で明示的に設定されていない場合のみ上書き
        org.bukkit.Location newStartLocation = (this.startLocation != null) ? this.startLocation : camera.getLocation();
        org.bukkit.inventory.ItemStack[] newSavedInventory = camera.getInventory().getContents();
        org.bukkit.inventory.ItemStack[] newSavedArmor = camera.getInventory().getArmorContents();

        stopPatrol(); // 既存タスクがあれば停止（startLocation がここでnullになる）
        stopTracking(); // 追跡タスクも停止

        // stopPatrol() の後にあらためて設定（クリアされても確実に保持）
        this.cameraUuid = camera.getUniqueId();
        this.startLocation = newStartLocation;
        this.savedInventory = newSavedInventory;
        this.savedArmor = newSavedArmor;

        plugin.getLogger().info("[Patrol] 開始地点を保存しました: "
                + newStartLocation.getWorld().getName()
                + " (" + String.format("%.1f", newStartLocation.getX())
                + ", " + String.format("%.1f", newStartLocation.getY())
                + ", " + String.format("%.1f", newStartLocation.getZ()) + ")");

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

        // パトロールの最初の実行をスケジュール
        scheduleNextTick(1L);

        plugin.getLogger().info("パトロールを開始しました。カメラ: " + camera.getName() + ", デフォルト間隔: " + dwellSeconds + "秒, 観光地数: "
                + touristLocations.size());
    }

    /**
     * 次のパトロール実行をスケジュールします。
     *
     * @param delayTicks 実行までのティック数
     */
    private void scheduleNextTick(long delayTicks) {
        if (patrolTask != null) {
            patrolTask.cancel();
        }
        patrolTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                int duration = tickPatrol();
                scheduleNextTick(Math.max(20, (long) duration * 20));
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "パトロール処理(tick)でエラーが発生しました: " + t.getMessage(), t);
                // エラー時はデフォルト秒数で次をスケジュール
                scheduleNextTick(20L * plugin.getTourConf().dwellSeconds);
            }
        }, delayTicks);
    }

    /**
     * パトロールを停止します。
     * <p>
     * 定期タスクをキャンセルし、カメラ役以外の全プレイヤーをサバイバルモードに戻します。
     * （以前発生した「全員スペクテイター化事故」への安全策）
     */
    public void stopPatrol() {
        if (plugin.getTickMonitor() != null) {
            plugin.getTickMonitor().resetPauseState();
        }

        if (patrolTask != null) {
            patrolTask.cancel();
            patrolTask = null;
        }

        stopTracking();

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
            // 安全のため、改めてサバイバル＆無敵解除を強制する
            camera.setGameMode(GameMode.SURVIVAL);
            try {
                camera.setInvulnerable(false);
                camera.setFlying(false);
                camera.setAllowFlight(false);
            } catch (Throwable ignored) {}

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
     * パトロール開始時の復帰地点を明示的に設定します。
     * 次回 startPatrol() を呼んだ際に、この場所が優先して使用されます。
     * 
     * @param loc 復帰地点
     */
    public void setStartLocation(org.bukkit.Location loc) {
        this.startLocation = loc;
        if (loc != null) {
            plugin.getLogger().info("[Patrol] 開始地点を手動設定しました: "
                    + loc.getWorld().getName()
                    + " (" + String.format("%.1f", loc.getX())
                    + ", " + String.format("%.1f", loc.getY())
                    + ", " + String.format("%.1f", loc.getZ()) + ")");
        }
    }

    /**
     * 現在保存されているパトロール開始地点（復帰地点）を取得します。
     * パトロール中でない場合は null を返します。
     * 
     * @return 開始地点、未設定の場合は null
     */
    public org.bukkit.Location getStartLocation() {
        return this.startLocation;
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
     * 
     * @return このスポットに滞在すべき秒数
     */
    private int tickPatrol() {
        Player camera = getCamera();
        if (camera == null || !camera.isOnline()) {
            return 8; // デフォルト
        }

        // 基本の滞在時間
        int staySeconds = plugin.getTourConf().dwellSeconds;

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

        // 次のサイクルに向けた事前読み込みをスケジュール（現在の滞在時間を考慮）
        schedulePreLoad(staySeconds);

        // 前の追跡タスクがあれば停止
        stopTracking();

        if (target != null) {
            // ターゲットが見つかった場合: プレイヤー観戦モード
            spectateTarget(camera, target);

            // 参加（映ったこと）を記録
            participationManager.noteParticipation(target.getUniqueId(), target.getName());

            // タイトル表示：プレイヤー名を大きく強調
            MessageUtils.showTitleLargeSmall(camera, "§b" + target.getName() + " §7さんの視点", "§aNow On Air");
            return staySeconds;
        }

        // ターゲットが見つからなかった場合、観光巡り（Tour）セクションへ進む
        // 低スペックモードでも、誰もいないときは観光地を映すように修正

        // 2. ターゲットがいなければ観光巡り：次のスポットへ
        if (touristLocations.isEmpty())
            return staySeconds;

        // 次の有効な観光地を探す（最大でリストサイズ分だけ試行）
        TouristLocation nextLocation = null;
        int attempts = 0;
        int listSize = touristLocations.size();

        while (attempts < listSize) {
            // インデックスを進める（ループする）
            currentTourIndex = (currentTourIndex + 1) % listSize;
            TouristLocation candidate = touristLocations.get(currentTourIndex);
            attempts++;

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
            return staySeconds;
        }

        // 個別の滞在時間があれば適用
        if (nextLocation.dwellSeconds != null && nextLocation.dwellSeconds > 0) {
            staySeconds = nextLocation.dwellSeconds;
        }

        World w = Bukkit.getWorld(nextLocation.world);
        if (w == null) {
            // 低スペックモード時は強制ロードを行わない(フリーズ防止)
            // ただし、エンドワールド(world_the_end)だけは特別に許可する
            if (plugin.getPerformanceConf().lowSpecMode && !nextLocation.world.equalsIgnoreCase("world_the_end")) {
                return staySeconds;
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
            return staySeconds;
        }

        // *** 特殊ロジック: エンドならドラゴンを探す ***
        if (w.getEnvironment() == World.Environment.THE_END) {
            org.bukkit.entity.EnderDragon dragon = w.getEntitiesByClass(org.bukkit.entity.EnderDragon.class)
                    .stream().findFirst().orElse(null);

            if (dragon != null && dragon.isValid()) {
                plugin.getLogger().info("[Patrol] エンダードラゴンを検知しました。追跡を開始します。");
                // 三人称視点（追跡モード）で映す
                startCinematicFollow(camera, dragon, "§5The Void Dragon", "§dエンドラを追跡中...");
                return staySeconds;
            } else {
                if (plugin.getPerformanceConf().debugLog) {
                    plugin.getLogger().info("[Patrol] エンドにいますが、エンダードラゴンが見つかりません。");
                }
            }
        }

        // *** 特殊ロジック: ネザー (ピグリンブルート) を探す ***
        if (w.getEnvironment() == World.Environment.NETHER) {
            org.bukkit.entity.PiglinBrute brute = w.getEntitiesByClass(org.bukkit.entity.PiglinBrute.class)
                    .stream().findFirst().orElse(null);

            if (brute != null && brute.isValid()) {
                // ピグリンブルートも三人称追跡
                startCinematicFollow(camera, brute, "§6砦の遺跡", "§eピグリンブルートを観測中...");
                return staySeconds;
            }
        }

        // *** 特殊ロジック: 海底神殿 (エルダーガーディアン) を探す ***
        if (w.getEnvironment() == World.Environment.NORMAL) {
            org.bukkit.entity.ElderGuardian elder = w.getEntitiesByClass(org.bukkit.entity.ElderGuardian.class)
                    .stream().findFirst().orElse(null);

            if (elder != null && elder.isValid()) {
                startCinematicFollow(camera, elder, "§b海底神殿", "§3エルダーガーディアンを観測中...");
                return staySeconds;
            }
        }

        // pitch が極端（真下/真上）になりすぎないよう補正：±85度にクリップ
        float safePitch = Math.max(-85f, Math.min(85f, nextLocation.pitch));

        // テレポート前に観戦状態を解除（視点ロック解除対策）
        stopSpectating(camera);

        // テレポート実行
        org.bukkit.Location loc = new org.bukkit.Location(w, nextLocation.x, nextLocation.y, nextLocation.z,
                nextLocation.yaw, safePitch);
        camera.setGameMode(GameMode.SPECTATOR); // 移動前にスペクテイターにしておく
        camera.teleport(loc);
        camera.setGameMode(GameMode.SPECTATOR); // 移動後も強制適用
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

        return staySeconds;
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

        // 視点切り替え失敗対策：次のターゲットへ移る前に、現在の観戦（SpectatorTarget）を解除する
        stopSpectating(camera);

        // 3. まず同じ場所にテレポートさせる（ワールド読み込み・チャンク同期のため）
        // 低スペックモード：移動が多すぎないよう最低限の遅延を入れる
        Location targetLoc = target.getLocation();
        if (camera.getWorld().equals(targetLoc.getWorld())) {
            camera.teleport(targetLoc);
        } else {
            // ワールドが異なる場合は1tick遅延実行（Paper/Magma負荷対策）
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (camera.isOnline() && target.isValid()) {
                    camera.setGameMode(GameMode.SPECTATOR); // テレポート前にスペクテイター化
                    camera.teleport(target.getLocation());
                    camera.setGameMode(GameMode.SPECTATOR); // ワールド移動直後に適用して落下防止
                    camera.setFlying(true);
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

    @org.bukkit.event.EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (cameraUuid != null && e.getPlayer().getUniqueId().equals(cameraUuid)) {
            // ワールド移動時にアーマースタンドを再生成するため、一旦停止
            stopTracking();
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerKick(org.bukkit.event.player.PlayerKickEvent e) {
        plugin.getLogger().warning("[KickLog] Player " + e.getPlayer().getName() + " was kicked: " + e.getReason());
        if (cameraUuid != null && e.getPlayer().getUniqueId().equals(cameraUuid)) {
            plugin.getLogger().warning("[KickLog] カメラ役がキックされました！再ログインを待ちます。");
            stopTracking();
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if (cameraUuid != null && e.getPlayer().getUniqueId().equals(cameraUuid)) {
            plugin.getLogger().warning("[QuitLog] カメラ役 " + e.getPlayer().getName() + " がログアウトしました。");
            stopTracking();
        }
    }

    @org.bukkit.event.EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        if (cameraUuid != null && e.getPlayer().getUniqueId().equals(cameraUuid)) {
            final Player camera = e.getPlayer();
            // リスポーン直後はSurvivalに戻されることが多いため、1tick後に強制的に戻す
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (camera.isOnline() && cameraUuid != null) {
                    camera.setGameMode(GameMode.SPECTATOR);
                    camera.setFlying(true);
                    plugin.getLogger().info("[Patrol] カメラ役のリスポーンを検知。パトロールを続行します。");
                    // 次の巡回を即座に実行して、死体ポイントから離脱させる
                    scheduleNextTick(1L);
                }
            }, 1L);
        }
    }

    /**
     * 指定したエンティティをシネマティックに追跡（3人称視点）します。
     */
    private void startCinematicFollow(Player camera, org.bukkit.entity.Entity target, String title, String subtitle) {
        stopTracking();
        stopSpectating(camera);

        // 常にターゲットの位置へ一度テレポさせて、チャンク読み込みを誘発する
        camera.teleport(target.getLocation());
        
        // 15tick（0.75秒）待機してから追跡を開始（クライアント側でのエンティティ同期待ち）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!camera.isOnline() || !target.isValid()) return;

            MessageUtils.showTitleLargeSmall(camera, title, subtitle);

            // カメラ用の透明アーマースタンドを生成
            org.bukkit.Location initialLoc = target.getLocation();
            cinematicCameraStand = initialLoc.getWorld().spawn(initialLoc, org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                // Markerをfalseにしてみる（spectateの安定性向上のため）
                stand.setMarker(false);
                stand.setInvulnerable(true);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.addScoreboardTag("patrol_cinematic_camera");
            });

            // プレイヤーにアーマースタンドを観戦させる
            camera.setSpectatorTarget(cinematicCameraStand);

            int updateInterval = plugin.getConfig().getInt("patrol.trackingUpdateIntervalTicks", 2);
            trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!camera.isOnline() || !target.isValid() || !camera.getGameMode().equals(GameMode.SPECTATOR)) {
                    stopTracking();
                    return;
                }

                // ターゲットの後方・上方にカメラを配置
                org.bukkit.util.Vector direction = target.getLocation().getDirection().normalize();
                double distance = (target instanceof org.bukkit.entity.EnderDragon) ? 15.0 : 6.0;
                double height = (target instanceof org.bukkit.entity.EnderDragon) ? 5.0 : 3.0;

                Location followLoc = target.getLocation().clone().subtract(direction.multiply(distance));
                followLoc.setY(followLoc.getY() + height);

                // ターゲットの方を向く
                org.bukkit.util.Vector lookDir = target.getLocation().toVector().subtract(followLoc.toVector());
                followLoc.setDirection(lookDir);

                // スムーズな移動
                if (cinematicCameraStand != null && cinematicCameraStand.isValid()) {
                    // ワールドが異なる場合は追跡不能なので停止
                    if (!cinematicCameraStand.getWorld().equals(target.getWorld())) {
                        stopTracking();
                        return;
                    }
                    
                    // 距離が極端に近い場合はテレポートをスキップしてパケット節約
                    if (cinematicCameraStand.getLocation().distanceSquared(followLoc) < 0.0001) {
                        return;
                    }
                    
                    cinematicCameraStand.teleport(followLoc);
                    if (camera.getSpectatorTarget() != cinematicCameraStand) {
                        camera.setSpectatorTarget(cinematicCameraStand);
                    }
                }
            }, 1L, (long) updateInterval);
        }, 15L);
    }

    private void stopTracking() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
        if (cinematicCameraStand != null) {
            cinematicCameraStand.remove();
            cinematicCameraStand = null;
        }
    }

    private void stopSpectating(Player camera) {
        if (camera != null && camera.getGameMode() == GameMode.SPECTATOR) {
            camera.setSpectatorTarget(null);
        }
    }

    @org.bukkit.event.EventHandler
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (cameraUuid != null && e.getEntity().getUniqueId().equals(cameraUuid)) {
            e.setCancelled(true);
        }
    }
}
