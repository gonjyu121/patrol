package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 配信用カメラを含むオンライン参加者全員へ直接メッセージを届ける。 */
public final class ParticipantMessageRouter {
    private ParticipantMessageRouter() {
    }

    public static void send(JavaPlugin plugin, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
}
