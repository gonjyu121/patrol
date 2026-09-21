package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/** Routes automatic plugin announcements to the configured stream camera only. */
public final class CameraMessageRouter {
    private CameraMessageRouter() {
    }

    public static void send(JavaPlugin plugin, String message) {
        Player camera = findCamera(plugin);
        if (camera != null) {
            camera.sendMessage(message);
        }
    }

    public static Collection<? extends Player> recipients(JavaPlugin plugin) {
        Player camera = findCamera(plugin);
        return camera == null ? List.of() : List.of(camera);
    }

    static boolean isCameraName(String configuredName, String actualName) {
        return configuredName != null && actualName != null && configuredName.equalsIgnoreCase(actualName);
    }

    private static Player findCamera(JavaPlugin plugin) {
        String configuredName = plugin.getConfig().getString("patrol.autoStart.cameraPlayerName", "OtouGame");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isCameraName(configuredName, player.getName())) {
                return player;
            }
        }
        return null;
    }
}
