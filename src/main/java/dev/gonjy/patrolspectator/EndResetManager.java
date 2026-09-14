package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
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
    private ResetCause resetCause = ResetCause.NONE;

    private enum ResetCause {
        NONE,
        DRAGON_DEATH,
        MANUAL;

        static ResetCause fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }

    public EndResetManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 保存されたリセット時刻のロード
        this.scheduledResetTime = plugin.getConfig().getLong("end.scheduledResetTime", 0);
        this.resetCause = ResetCause.fromConfig(plugin.getConfig().getString("end.scheduledResetCause"));

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
        if (scheduledResetTime > 0 && resetCause == ResetCause.NONE) {
            plugin.getLogger().warning("[EndReset] Discarding a legacy reset reservation without a trusted cause.");
            clearResetSchedule();
        }

        if (scheduledResetTime > 0) {
            // ワールドと既存ドラゴンがロードされるまで待ってから予約を再検証する。
            Bukkit.getScheduler().runTaskLater(plugin, this::resumePendingReset, 200L);
        } else {
            // リセット予定がない場合、ドラゴンの不在をチェック
            Bukkit.getScheduler().runTaskLater(plugin, this::checkDragonAbsence, 200L); // 10秒後
        }
    }

    private void resumePendingReset() {
        if (scheduledResetTime <= 0 || isResetting) {
            return;
        }

        World endWorld = getEndWorld();
        if (resetCause == ResetCause.DRAGON_DEATH) {
            if (endWorld == null) {
                plugin.getLogger().warning("[EndReset] End world is not ready; postponing pending reset validation.");
                Bukkit.getScheduler().runTaskLater(plugin, this::resumePendingReset, 1200L);
                return;
            }
            if (hasLiveDragon(endWorld)) {
                cancelAutomaticReset("a live Ender Dragon was found during startup validation");
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now >= scheduledResetTime) {
            plugin.getLogger().info("[EndReset] Valid pending end reset found. Resetting shortly...");
            resetTask = Bukkit.getScheduler().runTaskLater(plugin, this::performReset, 100L);
        } else {
            long delayTicks = Math.max(1L, (scheduledResetTime - now) / 50L);
            plugin.getLogger().info("[EndReset] Valid pending end reset found. Rescheduling in " + (delayTicks / 20) + " seconds.");
            scheduleResetTask(delayTicks);
            scheduleAnnouncements();
        }
    }

    private void startPeriodicCheck() {
        // 5分ごとにドラゴンの不在をチェック
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkDragonAbsence, 6000L, 6000L);
    }

    /**
     * ドラゴンがいない場合、または討伐済みの場合にリセットを開始する
     */
    void checkDragonAbsence() {
        if (isResetting) {
            return;
        }

        World endWorld = getEndWorld();
        if (endWorld == null) {
            return;
        }

        if (scheduledResetTime > 0) {
            if (resetCause == ResetCause.DRAGON_DEATH && hasLiveDragon(endWorld)) {
                cancelAutomaticReset("a live Ender Dragon was found during periodic validation");
            }
            return;
        }

        if (!hasLiveDragon(endWorld) && !isDragonRespawning(endWorld)) {
            // チャンク未ロードを「討伐」と誤認しないよう、不在推測だけではリセットしない。
            plugin.getLogger().warning("[EndReset] No loaded Ender Dragon was found, but no reset will be scheduled without a death event.");
        }
    }

    boolean hasLiveDragon(World endWorld) {
        if (endWorld == null) {
            return false;
        }
        for (EnderDragon dragon : endWorld.getEntitiesByClass(EnderDragon.class)) {
            if (dragon != null && dragon.isValid() && !dragon.isDead()) {
                return true;
            }
        }
        org.bukkit.boss.DragonBattle battle = endWorld.getEnderDragonBattle();
        if (battle == null) {
            return false;
        }
        EnderDragon battleDragon = battle.getEnderDragon();
        return battleDragon != null && battleDragon.isValid() && !battleDragon.isDead();
    }

    private boolean isDragonRespawning(World endWorld) {
        org.bukkit.boss.DragonBattle battle = endWorld.getEnderDragonBattle();
        return battle != null && battle.getRespawnPhase() != org.bukkit.boss.DragonBattle.RespawnPhase.NONE;
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
                World deathWorld = event.getEntity().getWorld();
                // 死亡した個体がエンティティ一覧から除かれた次のtickで、他の生存個体を確認する。
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (hasLiveDragon(deathWorld)) {
                        plugin.getLogger().warning("[EndReset] A dragon died, but another live dragon remains. Reset was not scheduled.");
                        return;
                    }
                    startAutomaticResetCountdown("エンダードラゴンが討伐されました！");
                });
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDragonSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)
                || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || isResetting
                || resetCause != ResetCause.DRAGON_DEATH) {
            return;
        }
        cancelAutomaticReset("a new Ender Dragon spawned");
    }

    /**
     * リセットカウントダウンを開始
     */
    public void startResetCountdown(String reason) {
        startResetCountdown(reason, ResetCause.MANUAL);
    }

    void startAutomaticResetCountdown(String reason) {
        startResetCountdown(reason, ResetCause.DRAGON_DEATH);
    }

    private void startResetCountdown(String reason, ResetCause cause) {
        if (scheduledResetTime > 0) {
            return; // 既にスケジュール済み
        }

        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 120);
        long delayTicks = resetDelayMinutes * 60 * 20L;
        this.scheduledResetTime = System.currentTimeMillis() + (resetDelayMinutes * 60 * 1000L);
        this.resetCause = cause;

        // 設定保存
        plugin.getConfig().set("end.scheduledResetTime", scheduledResetTime);
        plugin.getConfig().set("end.scheduledResetCause", resetCause.name());
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
        resetCause = ResetCause.MANUAL;
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

        if (resetCause == ResetCause.NONE) {
            plugin.getLogger().warning("[EndReset] Reset aborted because its cause is missing or untrusted.");
            clearResetSchedule();
            return;
        }

        World validationWorld = getEndWorld();
        if (resetCause == ResetCause.DRAGON_DEATH && hasLiveDragon(validationWorld)) {
            cancelAutomaticReset("a live Ender Dragon was found immediately before reset execution");
            return;
        }

        resetTask = null;
        isResetting = true;

        if (plugin.getEndGameManager() != null) {
            plugin.getEndGameManager().shutdown();
        }

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
            // 初期島チャンク (0, 0) をロード
            newEndWorld.loadChunk(0, 0, true);

            // DragonBattle の初期状態をセットアップ
            // ※ setPreviouslyKilled(false) を呼ぶとバニラが DragonBattle 経由でドラゴンを
            //   自動スポーンする。手動スポーンと重複しないよう、スポーンは遅延チェックに委ねる。
            org.bukkit.boss.DragonBattle battle = newEndWorld.getEnderDragonBattle();
            if (battle != null) {
                try {
                    battle.generateEndPortal(false);
                } catch (Exception e) {
                    plugin.getLogger().warning("[EndReset] Error generating end portal: " + e.getMessage());
                }
                try {
                    battle.resetCrystals();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EndReset] Error resetting crystals: " + e.getMessage());
                }
                try {
                    battle.setPreviouslyKilled(false);
                } catch (Exception e) {
                    plugin.getLogger().warning("[EndReset] Error setting previously killed flag: " + e.getMessage());
                }
            }

            plugin.getLogger().info("[EndReset] End world recreated, DragonBattle initialized, and spawn chunk loaded.");
        } else {
            plugin.getLogger().severe("[EndReset] Failed to recreate End world!");
        }

        // 難易度をランダムに決定 (50%の確率でハードモード)
        boolean isHardMode = new java.util.Random().nextBoolean();
        plugin.getConfig().set("end.difficulty", isHardMode ? "HARD" : "NORMAL");

        // リセット成功時にスケジュール情報をクリア＆完了時刻を記録
        scheduledResetTime = 0;
        resetCause = ResetCause.NONE;
        plugin.getConfig().set("end.scheduledResetTime", 0);
        plugin.getConfig().set("end.scheduledResetCause", ResetCause.NONE.name());
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

        // バニラの DragonBattle がドラゴンをスポーンするまで 3 秒 (60 tick) 待ち、
        // 存在しない場合のみフォールバックとして 1 体スポーンする。
        // これにより「DragonBattle 自動スポーン + 手動スポーン」による二重スポーンを防ぐ。
        final String finalWorldName = worldName;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = Bukkit.getWorld(finalWorldName);
            if (world == null) {
                plugin.getLogger().warning("[EndReset] End world not found after recreation for dragon check.");
                isResetting = false;
                return;
            }

            java.util.Collection<EnderDragon> dragons = world.getEntitiesByClass(EnderDragon.class);
            if (dragons.isEmpty()) {
                // DragonBattle がスポーンしなかった場合のフォールバック
                plugin.getLogger().info("[EndReset] No Ender Dragon found 3s after world creation. Spawning fallback dragon.");
                Location dragonSpawnLoc = new Location(world, 0.5, 100.0, 0.5);
                EnderDragon dragon = world.spawn(dragonSpawnLoc, EnderDragon.class, d -> {
                    d.setPhase(EnderDragon.Phase.CIRCLING);
                });
                plugin.getLogger().info("[EndReset] Spawned fallback Ender Dragon at: " + dragon.getLocation());
            } else {
                plugin.getLogger().info("[EndReset] Ender Dragon already present (" + dragons.size() + " entity). No fallback spawn needed.");
                if (dragons.size() > 1) {
                    // 万が一複数いた場合は1体を残して除去（安全策）
                    plugin.getLogger().warning("[EndReset] Multiple dragons detected (" + dragons.size() + "). Removing extras.");
                    boolean first = true;
                    for (EnderDragon d : dragons) {
                        if (first) { first = false; continue; }
                        d.remove();
                    }
                }
            }

            // EndGameManager の初期化と BossBar のセットアップ（ドラゴン確定後に実行）
            if (plugin.getEndGameManager() != null) {
                plugin.getEndGameManager().onEndRecreated(Bukkit.getWorld(finalWorldName));
            }

            isResetting = false;
        }, 60L); // 3 秒後にドラゴン存在確認
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
        clearResetSchedule();
        isResetting = false;
    }

    private void cancelAutomaticReset(String reason) {
        plugin.getLogger().warning("[EndReset] Automatic reset cancelled because " + reason + ".");
        clearResetSchedule();
    }

    private void clearResetSchedule() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        scheduledResetTime = 0;
        resetCause = ResetCause.NONE;
        plugin.getConfig().set("end.scheduledResetTime", 0);
        plugin.getConfig().set("end.scheduledResetCause", ResetCause.NONE.name());
        plugin.saveConfig();
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

