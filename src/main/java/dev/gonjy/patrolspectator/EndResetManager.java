package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.logging.Level;

/**
 * エンドワールドの自動リセットを管理するクラス。
 * <p>
 * エンダードラゴン討伐後、または不在検知後、一定時間経過後にエンドワールドを再生成します。
 */
public class EndResetManager implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private final String endWorldName;
    private int resetDelayMinutes;
    private BukkitTask resetTask;
    private boolean isResetting = false; // リセット処理中フラグ
    private long scheduledResetTime = 0; // リセット予定時刻（ミリ秒）

    public EndResetManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.endWorldName = "world_the_end"; // デフォルトのエンドワールド名
        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 保存されたリセット時刻のロード
        this.scheduledResetTime = plugin.getConfig().getLong("end.scheduledResetTime", 0);

        // 起動時のチェック
        checkOnStartup();

        // 定期チェック（ドラゴン不在など）
        startPeriodicCheck();
    }

    private void checkOnStartup() {
        if (scheduledResetTime > 0) {
            long now = System.currentTimeMillis();
            if (now >= scheduledResetTime) {
                // 時間が過ぎているので即リセット（少し遅延させる）
                plugin.getLogger().info("Pending end reset found. Resetting shortly.");
                Bukkit.getScheduler().runTaskLater(plugin, this::performReset, 100L);
            } else {
                // まだなのでタスク再スケジュール
                long delayTicks = (scheduledResetTime - now) / 50;
                plugin.getLogger().info("Pending end reset found. Rescheduling in " + (delayTicks / 20) + " seconds.");
                scheduleResetTask(delayTicks);
                scheduleAnnouncements();
            }
        } else {
            // リセット予定がない場合、ドラゴンの不在をチェック
            Bukkit.getScheduler().runTaskLater(plugin, this::checkDragonAbsence, 200L); // 10秒後
        }
    }

    private void startPeriodicCheck() {
        // 5分ごとにドラゴンの不在をチェック
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkDragonAbsence, 6000L, 6000L);
    }

    /**
     * ドラゴンがいない場合、リセットを開始する
     */
    private void checkDragonAbsence() {
        if (isResetting || scheduledResetTime > 0)
            return;

        // リセット直後（例えば5分以内）はチェックをスキップして、ドラゴンのスポーン待ち時間を確保する
        long lastResetTime = plugin.getConfig().getLong("end.lastResetTime", 0);
        if (System.currentTimeMillis() - lastResetTime < 5 * 60 * 1000L) {
            return;
        }

        World endWorld = Bukkit.getWorld(endWorldName);
        if (endWorld == null)
            return;

        // 誰もいない場合はチャンクがロードされていない可能性が高いためチェックしない
        // （誤検知による不要なリセットを防ぐ）
        if (endWorld.getPlayers().isEmpty()) {
            return;
        }

        // ドラゴンを探す
        boolean dragonExists = endWorld.getEntitiesByClass(EnderDragon.class).size() > 0;

        if (!dragonExists) {
            plugin.getLogger().info("Ender Dragon not found in " + endWorldName + ". Scheduling reset.");
            startResetCountdown("エンダードラゴンの不在を確認しました。");
        }
    }

    /**
     * エンダードラゴン討伐イベント
     */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            // エンドワールドでの討伐か確認
            if (event.getEntity().getWorld().getName().equals(endWorldName)) {
                startResetCountdown("エンダードラゴンが討伐されました！");
            }
        }
    }

    /**
     * リセットカウントダウンを開始
     */
    public void startResetCountdown(String reason) {
        if (scheduledResetTime > 0) {
            return; // 既にスケジュール済み
        }

        long delayTicks = resetDelayMinutes * 60 * 20L;
        this.scheduledResetTime = System.currentTimeMillis() + (resetDelayMinutes * 60 * 1000L);

        // 設定保存
        plugin.getConfig().set("end.scheduledResetTime", scheduledResetTime);
        plugin.saveConfig();

        // リセットタスクのスケジュール
        scheduleResetTask(delayTicks);

        // アナウンス開始
        Bukkit.broadcast(Component.text("========================================", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("🐉 " + reason, NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text("エンドワールドは " + resetDelayMinutes + "分後 にリセットされます。", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text("エリトラなどのアイテム回収はお早めにお願いします！", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text("========================================", NamedTextColor.RED));

        // 定期的なアナウンス（残り時間を通知）
        scheduleAnnouncements();
    }

    private void scheduleResetTask(long delayTicks) {
        if (resetTask != null)
            resetTask.cancel();
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, this::performReset, delayTicks);
    }

    private void scheduleAnnouncements() {
        long now = System.currentTimeMillis();
        long remainingMillis = scheduledResetTime - now;
        if (remainingMillis <= 0)
            return;

        int remainingMinutes = (int) (remainingMillis / 1000 / 60);

        int[] announceAtMinutes = { 10, 5, 3, 1 };
        for (int min : announceAtMinutes) {
            if (min < remainingMinutes) {
                long delay = (remainingMillis - (min * 60 * 1000L)) / 50;
                if (delay > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (scheduledResetTime > 0) {
                            Bukkit.broadcast(Component.text("[EndReset] ", NamedTextColor.RED)
                                    .append(Component.text("エンドリセットまで残り " + min + "分 です！", NamedTextColor.YELLOW)));
                        }
                    }, delay);
                }
            }
        }

        // 30秒前
        long delay30s = (remainingMillis - 30000L) / 50;
        if (delay30s > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (scheduledResetTime > 0) {
                    Bukkit.broadcast(Component.text("[EndReset] ", NamedTextColor.RED)
                            .append(Component.text("エンドリセットまで残り 30秒 です！退避してください！", NamedTextColor.YELLOW)));
                }
            }, delay30s);
        }
    }

    /**
     * リセット処理の実行
     */
    private void performReset() {
        resetTask = null;
        isResetting = true;

        // 設定クリア
        scheduledResetTime = 0;
        plugin.getConfig().set("end.scheduledResetTime", 0);
        plugin.saveConfig();

        World endWorld = Bukkit.getWorld(endWorldName);

        if (endWorld == null) {
            plugin.getLogger().warning("End world '" + endWorldName + "' not found. Skipping reset.");
            isResetting = false;
            return;
        }

        Bukkit.broadcast(Component.text("[EndReset] エンドワールドのリセットを開始します...", NamedTextColor.RED));

        // 1. プレイヤーを退避
        Location safeSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : endWorld.getPlayers()) {
            p.teleport(safeSpawn);
            p.sendMessage(Component.text("エンドワールドがリセットされるため、メインワールドに移動しました。", NamedTextColor.YELLOW));
        }

        // 2. ワールドのアンロード
        if (!Bukkit.unloadWorld(endWorld, false)) {
            plugin.getLogger().severe("Failed to unload End world! Reset aborted.");
            Bukkit.broadcast(Component.text("[EndReset] エンドワールドのアンロードに失敗しました。リセットを中止します。", NamedTextColor.DARK_RED));
            isResetting = false;
            return;
        }

        // 3. ファイル削除と再生成
        try {
            File worldFolder = new File(Bukkit.getWorldContainer(), endWorldName);

            // region (地形)
            File targetRegion = new File(worldFolder, "region");
            if (!targetRegion.exists())
                targetRegion = new File(worldFolder, "DIM1/region");
            if (targetRegion.exists()) {
                deleteDirectory(targetRegion);
                plugin.getLogger().info("Deleted region folder: " + targetRegion.getAbsolutePath());
            }

            // DIM1 (エンティティ等)
            File dim1 = new File(worldFolder, "DIM1");
            if (dim1.exists()) {
                deleteDirectory(dim1);
                plugin.getLogger().info("Deleted DIM1 folder: " + dim1.getAbsolutePath());
            }

            // level.dat (ドラゴン討伐状態など)
            File levelDat = new File(worldFolder, "level.dat");
            if (levelDat.exists()) {
                if (levelDat.delete()) {
                    plugin.getLogger().info("Deleted level.dat");
                } else {
                    plugin.getLogger().warning("Failed to delete level.dat");
                }
            }
            File levelDatOld = new File(worldFolder, "level.dat_old");
            if (levelDatOld.exists())
                levelDatOld.delete();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting world files", e);
        }

        // 4. ワールドの再ロード（作成）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World newEndWorld = Bukkit
                    .createWorld(new org.bukkit.WorldCreator(endWorldName).environment(World.Environment.THE_END));

            // エンダードラゴンを明示的にリスポーンさせる
            if (newEndWorld != null) {
                org.bukkit.boss.DragonBattle battle = newEndWorld.getEnderDragonBattle();
                if (battle != null) {
                    battle.initiateRespawn();
                    plugin.getLogger().info("Initiated Ender Dragon respawn sequence.");
                } else {
                    plugin.getLogger().warning("Could not get DragonBattle instance.");
                }
            }

            // 難易度をランダムに決定 (50%の確率でハードモード)
            boolean isHardMode = new java.util.Random().nextBoolean();
            plugin.getConfig().set("end.difficulty", isHardMode ? "HARD" : "NORMAL");

            // リセット完了時刻を記録
            plugin.getConfig().set("end.lastResetTime", System.currentTimeMillis());
            plugin.saveConfig();

            Bukkit.broadcast(Component.text("[EndReset] エンドワールドのリセットが完了しました！", NamedTextColor.GREEN));

            if (isHardMode) {
                Bukkit.broadcast(Component.text("⚠ エンドワールドから強大なエネルギー反応を検知しました... (HARD MODE)", NamedTextColor.RED));
            } else {
                Bukkit.broadcast(Component.text("エンドワールドのエネルギー反応は正常です。(NORMAL MODE)", NamedTextColor.GREEN));
            }

            isResetting = false;
        }, 40L); // 2秒後
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    public boolean isResetting() {
        return isResetting;
    }

    public void cancelReset() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        scheduledResetTime = 0;
        plugin.getConfig().set("end.scheduledResetTime", 0);
        plugin.saveConfig();
        isResetting = false;
    }

    /**
     * リセットまでの残り時間を取得します（ミリ秒）。
     * 予定がない場合は -1 を返します。
     */
    public long getRemainingResetTimeMillis() {
        if (scheduledResetTime <= 0)
            return -1;
        return Math.max(0, scheduledResetTime - System.currentTimeMillis());
    }

    /**
     * 手動でリセットカウントダウンを開始します。
     */
    public void forceReset() {
        startResetCountdown("手動リセットが実行されました。");
    }
}
