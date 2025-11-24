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
 * エンドワールドの自動リセットを管理するクラス。
 * <p>
 * エンダードラゴン討伐後、一定時間経過後にエンドワールドを再生成します。
 */
public class EndResetManager implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private final String endWorldName;
    private int resetDelayMinutes;
    private BukkitTask resetTask;
    private long resetTime; // リセット実行予定時刻 (ms)
    private boolean isResetting = false; // リセット処理中フラグ

    public EndResetManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.endWorldName = "world_the_end"; // デフォルトのエンドワールド名
        this.resetDelayMinutes = plugin.getConfig().getInt("end.resetDelayMinutes", 20);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * エンダードラゴン討伐イベント
     */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            // エンドワールドでの討伐か確認
            if (event.getEntity().getWorld().getName().equals(endWorldName)) {
                startResetCountdown();
            }
        }
    }

    /**
     * リセットカウントダウンを開始
     */
    public void startResetCountdown() {
        if (resetTask != null && !resetTask.isCancelled()) {
            return; // 既にスケジュール済み
        }

        long delayTicks = resetDelayMinutes * 60 * 20L;
        resetTime = System.currentTimeMillis() + (resetDelayMinutes * 60 * 1000L);

        // リセットタスクのスケジュール
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, this::performReset, delayTicks);

        // アナウンス開始
        Bukkit.broadcastMessage(ChatColor.RED + "========================================");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🐉 エンダードラゴンが討伐されました！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "エンドワールドは " + resetDelayMinutes + "分後 にリセットされます。");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "エリトラなどのアイテム回収はお早めにお願いします！");
        Bukkit.broadcastMessage(ChatColor.RED + "========================================");

        // 定期的なアナウンス（残り時間を通知）
        scheduleAnnouncements();
    }

    private void scheduleAnnouncements() {
        int[] announceAtMinutes = { 10, 5, 3, 1 };
        for (int min : announceAtMinutes) {
            if (min < resetDelayMinutes) {
                long delay = (resetDelayMinutes - min) * 60 * 20L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (resetTask != null && !resetTask.isCancelled()) {
                        Bukkit.broadcastMessage(ChatColor.RED + "[EndReset] " + ChatColor.YELLOW + "エンドリセットまで残り " + min
                                + "分 です！");
                    }
                }, delay);
            }
        }

        // 30秒前
        long delay30s = (resetDelayMinutes * 60 - 30) * 20L;
        if (delay30s > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (resetTask != null && !resetTask.isCancelled()) {
                    Bukkit.broadcastMessage(
                            ChatColor.RED + "[EndReset] " + ChatColor.YELLOW + "エンドリセットまで残り 30秒 です！退避してください！");
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
        World endWorld = Bukkit.getWorld(endWorldName);

        if (endWorld == null) {
            plugin.getLogger().warning("End world '" + endWorldName + "' not found. Skipping reset.");
            isResetting = false;
            return;
        }

        Bukkit.broadcastMessage(ChatColor.RED + "[EndReset] エンドワールドのリセットを開始します...");

        // 1. プレイヤーを退避
        Location safeSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : endWorld.getPlayers()) {
            p.teleport(safeSpawn);
            p.sendMessage(ChatColor.YELLOW + "エンドワールドがリセットされるため、メインワールドに移動しました。");
        }

        // 2. ワールドのアンロード
        if (!Bukkit.unloadWorld(endWorld, false)) {
            plugin.getLogger().severe("Failed to unload End world! Reset aborted.");
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[EndReset] エンドワールドのアンロードに失敗しました。リセットを中止します。");
            isResetting = false;
            return;
        }

        // 3. ファイル削除と再生成（非同期で行うと安全だが、BukkitAPI操作を含むため同期でやるか、慎重に）
        // ファイル操作は重いので非同期でやりたいが、再ロードはメインスレッド必須。
        // ここではシンプルにメインスレッドで実行する（ラグる可能性あり）。
        try {
            File worldFolder = new File(Bukkit.getWorldContainer(), endWorldName);
            File regionFolder = new File(worldFolder, "DIM1/region"); // Vanilla structure usually inside DIM1
            // Spigot often puts region files directly in world_the_end/DIM1/region or
            // world_the_end/region depending on
            // config.
            // Check standard Bukkit structure: root/world_name/region (for nether/end if
            // separate folders)
            // or root/world_name/DIM1/region (if using vanilla layout)

            // Try to find region folder
            File targetRegion = new File(worldFolder, "region");
            if (!targetRegion.exists()) {
                targetRegion = new File(worldFolder, "DIM1/region");
            }

            if (targetRegion.exists()) {
                deleteDirectory(targetRegion);
                plugin.getLogger().info("Deleted region folder: " + targetRegion.getAbsolutePath());
            } else {
                plugin.getLogger().warning("Region folder not found for deletion: " + targetRegion.getAbsolutePath());
            }

            // level.dat を削除するとシード値などが変わる可能性があるが、今回は地形リセットが主目的なのでregion削除で十分か？
            // エンドラ復活のためには level.dat 内の DragonFight データを消す必要があるかもしれない。
            // 確実なのは DIM1 フォルダごと消すこと。
            File dim1 = new File(worldFolder, "DIM1");
            if (dim1.exists()) {
                deleteDirectory(dim1);
                plugin.getLogger().info("Deleted DIM1 folder: " + dim1.getAbsolutePath());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting world files", e);
        }

        // 4. ワールドの再ロード（作成）
        // 少し待ってからロード（ファイルロック回避のため）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.createWorld(new org.bukkit.WorldCreator(endWorldName).environment(World.Environment.THE_END));
            Bukkit.broadcastMessage(ChatColor.GREEN + "[EndReset] エンドワールドのリセットが完了しました！");
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
        isResetting = false;
    }
}
