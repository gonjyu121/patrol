package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * エンドワールドの自動リセット・再作成を管理するクラス。
 * <p>
 * エンダードラゴン討伐後、または不在検知後、一定時間経過後にエンドワールドを再生成します。
 * 手動での即時再作成コマンドにも対応しています。
 */
public class EndResetManager implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private int resetDelayMinutes;
    private BukkitTask resetTask;
    private boolean isResetting = false; // リセット処理中フラグ
    private long scheduledResetTime = 0; // リセット予定時刻（ミリ秒）

    public EndResetManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 保存されたリセット時刻のロード
        this.scheduledResetTime = plugin.getConfig().getLong("end.scheduledResetTime", 0);

        // 起動時のチェック
        checkOnStartup();

        // 定期チェック（ドラゴン不在など）
        startPeriodicCheck();
    }

    /**
     * 対象のエンドワールドを取得します。
     */
    public World getEndWorld() {
        String configuredName = plugin.getConfig().getString("end.worldName", "world_the_end");
        World endWorld = Bukkit.getWorld(configuredName);
        if (endWorld != null) {
            return endWorld;
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.THE_END) {
                return w;
            }
        }
        return Bukkit.getWorld("world_the_end");
    }

    public String getEndWorldName() {
        World w = getEndWorld();
        return w != null ? w.getName() : plugin.getConfig().getString("end.worldName", "world_the_end");
    }

    private void checkOnStartup() {
        if (scheduledResetTime > 0) {
            long now = System.currentTimeMillis();
            if (now >= scheduledResetTime) {
                // 時間が過ぎているので即リセット（サーバー起動安定のため5秒遅延）
                plugin.getLogger().info("[EndReset] Pending end reset found. Resetting shortly...");
                Bukkit.getScheduler().runTaskLater(plugin, this::performReset, 100L);
            } else {
                // まだなのでタスク再スケジュール
                long delayTicks = (scheduledResetTime - now) / 50;
                plugin.getLogger().info("[EndReset] Pending end reset found. Rescheduling in " + (delayTicks / 20) + " seconds.");
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
     * ドラゴンがいない場合、または討伐済みの場合にリセットを開始する
     */
    private void checkDragonAbsence() {
        if (isResetting || scheduledResetTime > 0) {
            return;
        }

        World endWorld = getEndWorld();
        if (endWorld == null) {
            return;
        }

        // リセット直後（例えば5分以内）はチェックをスキップして、ドラゴンのスポーン・初期化時間を確保する
        long lastResetTime = plugin.getConfig().getLong("end.lastResetTime", 0);
        long now = System.currentTimeMillis();
        if (lastResetTime > 0 && (now - lastResetTime < 5 * 60 * 1000L)) {
            return;
        }

        org.bukkit.boss.DragonBattle battle = endWorld.getEnderDragonBattle();
        boolean dragonKilled = false;

        if (battle != null) {
            // DragonBattle が存在する場合: 初回ドラゴンが討伐済みかどうか確認
            if (battle.hasBeenPreviouslyKilled()) {
                // 討伐済みの場合、現在生きているドラゴンがいるか、または復活の儀式中か確認
                boolean hasLiveDragon = !endWorld.getEntitiesByClass(EnderDragon.class).isEmpty();
                boolean isRespawning = battle.getRespawnPhase() != org.bukkit.boss.DragonBattle.RespawnPhase.NONE;

                if (!hasLiveDragon && !isRespawning) {
                    dragonKilled = true;
                }
            }
        } else {
            // DragonBattle が取得できない場合、ロード済みエンティティにドラゴンがいないか確認
            boolean hasLiveDragon = !endWorld.getEntitiesByClass(EnderDragon.class).isEmpty();
            if (!hasLiveDragon) {
                dragonKilled = true;
            }
        }

        if (dragonKilled) {
            // 討伐済みでドラゴンが不在であることを確認
            // 前回リセット時刻から既に resetDelayMinutes 以上経過している場合（または lastResetTime が未記録の場合）は即リセット
            this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
            long delayMillis = resetDelayMinutes * 60 * 1000L;

            if (lastResetTime == 0 || (now - lastResetTime >= delayMillis)) {
                plugin.getLogger().info("[EndReset] Dragon was previously defeated and delay timer has expired. Initiating End recreation in 10 seconds...");
                Bukkit.broadcastMessage(ChatColor.RED + "[EndReset] " + ChatColor.YELLOW + "エンドワールドが討伐完了状態のため、10秒後に再生成を開始します。");
                if (plugin.getDiscordWebhookClient() != null) {
                    plugin.getDiscordWebhookClient().send("🔄 **[End Reset]** エンドワールドが討伐完了状態であることを検知しました。10秒後に再生成を開始します。");
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::performReset, 200L); // 10秒後に即リセット
            } else {
                // まだリセット猶予時間内であれば、残りの時間でカウントダウンを開始
                long remainingMillis = (lastResetTime + delayMillis) - now;
                long delayTicks = remainingMillis / 50;
                this.scheduledResetTime = now + remainingMillis;
                plugin.getConfig().set("end.scheduledResetTime", scheduledResetTime);
                plugin.saveConfig();

                plugin.getLogger().info("[EndReset] Dragon absence detected. Scheduling reset in " + (remainingMillis / 1000 / 60) + " minutes.");
                scheduleResetTask(delayTicks);
                scheduleAnnouncements();
            }
        }
    }

    /**
     * エンダードラゴン討伐イベント
     */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            World endWorld = getEndWorld();
            String endName = endWorld != null ? endWorld.getName() : getEndWorldName();
            if (event.getEntity().getWorld().getName().equals(endName)) {
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

        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
        long delayTicks = resetDelayMinutes * 60 * 20L;
        this.scheduledResetTime = System.currentTimeMillis() + (resetDelayMinutes * 60 * 1000L);

        // 設定保存
        plugin.getConfig().set("end.scheduledResetTime", scheduledResetTime);
        plugin.saveConfig();

        // リセットタスクのスケジュール
        scheduleResetTask(delayTicks);

        // アナウンス開始
        Bukkit.broadcastMessage(ChatColor.RED + "========================================");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🐉 " + reason);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "エンドワールドは " + resetDelayMinutes + "分後 にリセットされます。");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "エリトラなどのアイテム回収はお早めにお願いします！");
        Bukkit.broadcastMessage(ChatColor.RED + "========================================");

        if (plugin.getDiscordWebhookClient() != null) {
            plugin.getDiscordWebhookClient().send("🐉 **[End Reset Scheduled]** " + reason + " エンドワールドは **" + resetDelayMinutes + "分後** に再生成されます。");
        }

        // 定期的なアナウンス（残り時間を通知）
        scheduleAnnouncements();
    }

    private void scheduleResetTask(long delayTicks) {
        if (resetTask != null) {
            resetTask.cancel();
        }
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, this::performReset, delayTicks);
    }

    private void scheduleAnnouncements() {
        long now = System.currentTimeMillis();
        long remainingMillis = scheduledResetTime - now;
        if (remainingMillis <= 0) {
            return;
        }

        int remainingMinutes = (int) (remainingMillis / 1000 / 60);

        int[] announceAtMinutes = { 60, 30, 10, 5, 3, 1 };
        for (int min : announceAtMinutes) {
            if (min < remainingMinutes) {
                long delay = (remainingMillis - (min * 60 * 1000L)) / 50;
                if (delay > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (scheduledResetTime > 0) {
                            Bukkit.broadcastMessage(ChatColor.RED + "[EndReset] " + ChatColor.YELLOW + "エンドリセットまで残り " + min + "分 です！");
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
                    Bukkit.broadcastMessage(ChatColor.RED + "[EndReset] " + ChatColor.YELLOW + "エンドリセットまで残り 30秒 です！退避してください！");
                }
            }, delay30s);
        }
    }

    /**
     * 即座にエンドワールドの再生成を実行します（手動実行用）。
     */
    public void performResetNow() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        performReset();
    }

    /**
     * リセット処理の実行
     */
    private void performReset() {
        if (isResetting) {
            plugin.getLogger().warning("[EndReset] Reset is already in progress.");
            return;
        }

        resetTask = null;
        isResetting = true;

        World endWorld = getEndWorld();
        String endWorldName = endWorld != null ? endWorld.getName() : getEndWorldName();

        if (endWorld == null) {
            plugin.getLogger().warning("[EndReset] End world '" + endWorldName + "' not found. Trying to create directly...");
            createEndWorld(endWorldName);
            return;
        }

        // 事前通知
        String msg = "[EndReset] エンドワールドのリセット処理を開始します。一時的にサーバーが重くなる可能性があります。";
        Bukkit.broadcastMessage(ChatColor.RED + msg);
        if (plugin.getDiscordWebhookClient() != null) {
            plugin.getDiscordWebhookClient().send("🔄 **End Resetting...** World maintenance in progress.");
        }

        // 1. プレイヤーを安全に退避
        Location safeSpawn = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        if (safeSpawn == null) {
            safeSpawn = new Location(endWorld, 0, 100, 0);
        }

        for (Player p : endWorld.getPlayers()) {
            p.teleport(safeSpawn);
            p.sendMessage(ChatColor.YELLOW + "[EndReset] エンドワールドがリセットされるため、メインワールドに移動しました。");
        }

        // 2. プレイヤーのテレポート完了とチャンクチケット解放を待つため、20tick (1秒) 後にアンロードを実行
        final Location finalSafeSpawn = safeSpawn;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            executeWorldUnloadAndRecreate(endWorldName, finalSafeSpawn, 0);
        }, 20L);
    }

    /**
     * ワールドのアンロードと再作成を実行（リトライ機能付き）
     */
    private void executeWorldUnloadAndRecreate(String worldName, Location safeSpawn, int retryCount) {
        World endWorld = Bukkit.getWorld(worldName);

        if (endWorld != null) {
            // 残っているプレイヤーを再度退避
            for (Player p : endWorld.getPlayers()) {
                p.teleport(safeSpawn);
            }

            // ワールドのアンロード
            boolean unloaded = Bukkit.unloadWorld(endWorld, false);
            if (!unloaded) {
                if (retryCount < 2) {
                    plugin.getLogger().warning("[EndReset] Failed to unload End world on attempt " + (retryCount + 1) + ". Retrying in 1 second...");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        executeWorldUnloadAndRecreate(worldName, safeSpawn, retryCount + 1);
                    }, 20L);
                    return;
                } else {
                    plugin.getLogger().severe("[EndReset] Failed to unload End world after multiple attempts! Reset aborted.");
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "[EndReset] エンドワールドのアンロードに失敗しました。リセットを中止します。");
                    isResetting = false;
                    return;
                }
            }
            plugin.getLogger().info("[EndReset] Successfully unloaded world: " + worldName);
        }

        // 3. ファイル削除（非同期で実行）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("[EndReset] Starting asynchronous end world file deletion for: " + worldName);
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

                if (worldFolder.exists()) {
                    // ディレクトリ群の削除: region, entities, poi, data, DIM1
                    String[] dirsToDelete = { "region", "entities", "poi", "data", "DIM1" };
                    for (String dirName : dirsToDelete) {
                        File targetDir = new File(worldFolder, dirName);
                        if (targetDir.exists()) {
                            deleteDirectoryWithRetry(targetDir);
                            plugin.getLogger().info("[EndReset] Deleted folder: " + targetDir.getName());
                        }
                    }

                    // ファイル群の削除: level.dat, level.dat_old, session.lock, uid.dat
                    String[] filesToDelete = { "level.dat", "level.dat_old", "session.lock", "uid.dat" };
                    for (String fileName : filesToDelete) {
                        File targetFile = new File(worldFolder, fileName);
                        if (targetFile.exists()) {
                            deleteFileWithRetry(targetFile);
                            plugin.getLogger().info("[EndReset] Deleted file: " + targetFile.getName());
                        }
                    }
                }

                plugin.getLogger().info("[EndReset] Asynchronous file deletion completed.");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[EndReset] Error deleting world files asynchronously", e);
            }

            // 4. ワールドの再ロード（メインスレッドで実行）
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                createEndWorld(worldName);
            }, 20L); // 削除完了後、1秒待ってから再生成
        });
    }

    /**
     * エンドワールドを新規作成して初期化します。
     */
    private void createEndWorld(String worldName) {
        plugin.getLogger().info("[EndReset] Recreating End world: " + worldName + "...");
        World newEndWorld = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName).environment(World.Environment.THE_END));

        if (newEndWorld != null) {
            // 初期島チャンク (0, 0) をロードしてワールド生成と初代ドラゴンの自然スポーンをトリガー
            newEndWorld.loadChunk(0, 0);
            plugin.getLogger().info("[EndReset] End world recreated and spawn chunk loaded.");
        } else {
            plugin.getLogger().severe("[EndReset] Failed to recreate End world!");
        }

        // 難易度をランダムに決定 (50%の確率でハードモード)
        boolean isHardMode = new java.util.Random().nextBoolean();
        plugin.getConfig().set("end.difficulty", isHardMode ? "HARD" : "NORMAL");

        // リセット成功時にスケジュール情報をクリア＆完了時刻を記録
        scheduledResetTime = 0;
        plugin.getConfig().set("end.scheduledResetTime", 0);
        plugin.getConfig().set("end.lastResetTime", System.currentTimeMillis());
        plugin.saveConfig();

        Bukkit.broadcastMessage(ChatColor.GREEN + "[EndReset] エンドワールドのリセットが完了しました！");

        if (isHardMode) {
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ エンドワールドから強大なエネルギー反応を検知しました... (HARD MODE)");
            if (plugin.getDiscordWebhookClient() != null) {
                plugin.getDiscordWebhookClient().send("🐉 **The Void Dragon** has appeared! (Difficulty: **HARD**)");
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "エンドワールドのエネルギー反応は正常です。(NORMAL MODE)");
            if (plugin.getDiscordWebhookClient() != null) {
                plugin.getDiscordWebhookClient().send("🐉 **The Void Dragon** has appeared! (Difficulty: Normal)");
            }
        }

        isResetting = false;
    }

    private void deleteDirectoryWithRetry(File dir) {
        for (int attempt = 0; attempt < 3; attempt++) {
            deleteDirectory(dir);
            if (!dir.exists()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void deleteFileWithRetry(File file) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!file.exists() || file.delete()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }
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
            plugin.getLogger().warning("[EndReset] Failed to delete file/dir: " + file.getAbsolutePath());
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
        if (scheduledResetTime <= 0) {
            return -1;
        }
        return Math.max(0, scheduledResetTime - System.currentTimeMillis());
    }

    /**
     * 手動でリセットカウントダウンを開始します。
     */
    public void forceReset() {
        startResetCountdown("手動リセットが実行されました。");
    }

    public int getResetDelayMinutes() {
        return resetDelayMinutes;
    }
}

