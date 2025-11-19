package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class GameModeEnforcer {
    private final Plugin plugin;
    private BukkitTask task;
    private UUID cameraOperator;

    public GameModeEnforcer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            UUID cam = cameraOperator;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (cam != null && p.getUniqueId().equals(cam))
                    continue; // カメラは除外
                if (p.getGameMode() != GameMode.SURVIVAL) {
                    try {
                        p.setGameMode(GameMode.SURVIVAL);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 20L, 20L); // 1秒周期
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
    }

    public void setCameraOperator(UUID uuid) {
        this.cameraOperator = uuid;
    }

    public void clearCameraOperator() {
        this.cameraOperator = null;
    }

    /**
     * 指定されたプレイヤーがカメラ役でない場合、サバイバルモードに強制します。
     */
    public void ensurePlayerIsSurvival(Player p) {
        if (p == null)
            return;
        if (cameraOperator != null && cameraOperator.equals(p.getUniqueId()))
            return;

        if (p.getGameMode() != GameMode.SURVIVAL) {
            try {
                p.setGameMode(GameMode.SURVIVAL);
            } catch (Throwable ignored) {
            }
        }
    }
}
