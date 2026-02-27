package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * サーバーの Tick (MSPT) を監視し、負荷が高い場合にパトロールを自動停止する安全装置。
 */
public class TickMonitor {

    private final PatrolSpectatorPlugin plugin;
    private BukkitTask task;
    private int highLoadCount = 0;
    private static final int THRESHOLD_MS = 50;
    private static final int MAX_HIGH_LOAD_CHECKS = 5; // 50ms超えが5回連続（約5秒）で停止

    public TickMonitor(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getPatrolManager().isRunning()) {
                highLoadCount = 0;
                return;
            }

            double averageTickTime = Bukkit.getAverageTickTime();
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

    private void stopPatrolDueToLoad(double mspt) {
        plugin.getLogger().severe("[⚠️] サーバー負荷（MSPT: " + String.format("%.2f", mspt) + "ms）を検知したため、パトロールを自動停止しました。");

        Player camera = plugin.getPatrolManager().getCameraPlayer();
        if (camera != null) {
            camera.sendMessage(
                    Component.text("⚠️ サーバー負荷が高いため、パトロールを自動的に一時停止しました。 (MSPT: " + String.format("%.2f", mspt) + "ms)",
                            NamedTextColor.RED));
        }

        plugin.getPatrolManager().stopPatrol();
        highLoadCount = 0;
    }
}
