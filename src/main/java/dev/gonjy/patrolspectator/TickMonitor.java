package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * サーバーの Tick (MSPT) を監視し、負荷が高い場合にパトロールを自動停止する安全装置。
 */
public class TickMonitor {

    private final PatrolSpectatorPlugin plugin;
    private BukkitTask task;
    private int highLoadCount = 0;
    private int lowLoadCount = 0;
    private boolean isPausedDueToLoad = false;
    private UUID pausedCameraId = null;

    private static final int THRESHOLD_MS = 50;
    private static final int MAX_HIGH_LOAD_CHECKS = 5; // 50ms超えが5回連続（約10秒）で停止
    private static final double RESUME_THRESHOLD_MS = 40.0; // 40ms以下で安定とみなす
    private static final int MAX_LOW_LOAD_CHECKS = 5; // 40ms以下が5回連続（約10秒）で再開

    public TickMonitor(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double averageTickTime = Bukkit.getAverageTickTime();

            if (!plugin.getPatrolManager().isRunning()) {
                if (isPausedDueToLoad) {
                    if (averageTickTime <= RESUME_THRESHOLD_MS) {
                        lowLoadCount++;
                        if (lowLoadCount >= MAX_LOW_LOAD_CHECKS) {
                            resumePatrol();
                        }
                    } else {
                        lowLoadCount = 0;
                    }
                } else {
                    highLoadCount = 0;
                    lowLoadCount = 0;
                }
                return;
            }

            // 実行中の負荷チェック
            if (averageTickTime > THRESHOLD_MS) {
                highLoadCount++;
                if (plugin.getPerformanceConf().debugLog) {
                    plugin.getLogger()
                            .warning("[Performance] High load detected: " + String.format("%.2f", averageTickTime)
                                    + "ms (" + highLoadCount + "/" + MAX_HIGH_LOAD_CHECKS + ")");
                }

                if (highLoadCount >= MAX_HIGH_LOAD_CHECKS) {
                    stopPatrolDueToLoad(averageTickTime);
                }
            } else {
                highLoadCount = 0;
            }
        }, 40L, 40L); // 2秒ごとにチェック (CPU負荷削減)
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void resetPauseState() {
        isPausedDueToLoad = false;
        pausedCameraId = null;
        lowLoadCount = 0;
        highLoadCount = 0;
    }

    private void stopPatrolDueToLoad(double mspt) {
        plugin.getLogger().severe("[⚠️] サーバー負荷（MSPT: " + String.format("%.2f", mspt) + "ms）を検知したため、パトロールを自動停止しました。");

        Player camera = plugin.getPatrolManager().getCameraPlayer();
        if (camera != null) {
            pausedCameraId = camera.getUniqueId();
            isPausedDueToLoad = true;
            camera.sendMessage(ChatColor.RED + "⚠️ サーバー負荷が高いため、パトロールを自動的に一時停止しました。負荷が落ち着くと自動で再開します...");
        } else {
            isPausedDueToLoad = false;
        }

        plugin.getPatrolManager().stopPatrol();
        highLoadCount = 0;
        lowLoadCount = 0;
        
        // パトロール停止後も一時停止状態を維持するために再設定 (stopPatrol()内でresetPauseStateが呼ばれるため)
        if (pausedCameraId != null) {
            isPausedDueToLoad = true;
        }
    }

    private void resumePatrol() {
        isPausedDueToLoad = false;
        lowLoadCount = 0;
        highLoadCount = 0;

        if (pausedCameraId == null) return;

        Player camera = Bukkit.getPlayer(pausedCameraId);
        if (camera != null && camera.isOnline()) {
            plugin.getLogger().info("[✅] サーバー負荷が安定したため、パトロールを自動再開します。");
            camera.sendMessage(ChatColor.GREEN + "✅ サーバー負荷が安定したため、パトロールを自動再開します！");
            
            int dwellSeconds = plugin.getTourConf().dwellSeconds;
            plugin.getPatrolManager().startPatrol(camera, dwellSeconds);
        }
        
        pausedCameraId = null;
    }
}
