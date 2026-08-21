package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("deprecation")
public class DiscordListener implements Listener {

    private final JavaPlugin plugin;
    private final DiscordWebhookClient webhookClient;
    private final Set<Integer> crowdAlertThresholds = new HashSet<>();

    public DiscordListener(JavaPlugin plugin, DiscordWebhookClient webhookClient) {
        this.plugin = plugin;
        this.webhookClient = webhookClient;

        // Default thresholds: 3, 5, 10, 15, 20...
        crowdAlertThresholds.add(3);
        crowdAlertThresholds.add(5);
        crowdAlertThresholds.add(10);
        crowdAlertThresholds.add(15);
        crowdAlertThresholds.add(20);
        crowdAlertThresholds.add(25);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("discord.notifications.crowd_alert", true)) {
            return;
        }

        int onlineCount = Bukkit.getOnlinePlayers().size();

        // Check if current count hits a threshold
        if (crowdAlertThresholds.contains(onlineCount)) {
            String ip = plugin.getConfig().getString("discord.server_ip", "your.server.ip");
            int port = plugin.getServer().getPort();
            String address = ip + ":" + port;

            String msg = String.format("🎉 人数通知: 現在 %d人 がオンラインです！\n参加はこちら: `%s`",
                    onlineCount, address);
            webhookClient.send(msg);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Chat notification enabled by default or specific config
        if (!plugin.getConfig().getBoolean("discord.notifications.chat", true)) {
            return;
        }

        String playerName = event.getPlayer().getName();
        String message = event.getMessage();
        // Format: 💬 Player: Message (Optimized for OneComme TTS)
        webhookClient.send("💬 " + playerName + ": " + message);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("discord.notifications.quit", true)) {
            return;
        }

        String cameraName = plugin.getConfig().getString("patrol.autoStart.cameraPlayerName", "OtouGame");
        String playerName = event.getPlayer().getName();

        if (playerName.equalsIgnoreCase(cameraName)) {
            String msg = String.format("⚠️ **警告: カメラアカウント (%s) が切断されました！**\n配信が停止している可能性があります。", playerName);
            webhookClient.send(msg);
        }
    }
}
