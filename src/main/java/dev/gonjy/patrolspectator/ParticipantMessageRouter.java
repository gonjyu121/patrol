package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 配信用カメラを除外して、参加者だけへ直接メッセージを届ける。 */
public final class ParticipantMessageRouter {
    private ParticipantMessageRouter() {
    }

    public static void send(JavaPlugin plugin, String message) {
        String configuredCamera = plugin.getConfig()
                .getString("patrol.autoStart.cameraPlayerName", "OtouGame");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isParticipantName(configuredCamera, player.getName())) {
                player.sendMessage(message);
            }
        }
    }

    static boolean isParticipantName(String configuredCamera, String actualName) {
        return actualName != null && !CameraMessageRouter.isCameraName(configuredCamera, actualName);
    }
}
