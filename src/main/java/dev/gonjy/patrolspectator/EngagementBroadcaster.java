package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

/**
 * Handles periodic in-game announcements to encourage YouTube engagement.
 */
public class EngagementBroadcaster {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private BukkitTask broadcastTask;

    public EngagementBroadcaster(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the periodic broadcast task based on config.
     */
    public void start() {
        stop();

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("engagement.enabled", true)) {
            return;
        }

        int intervalMinutes = config.getInt("engagement.interval_minutes", 15);
        if (intervalMinutes <= 0) return;

        long ticks = intervalMinutes * 60L * 20L;

        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastRandomMessage, ticks, ticks);
        plugin.getLogger().info("[Engagement] Periodic broadcasts started every " + intervalMinutes + " minutes.");
    }

    /**
     * Stops the periodic broadcast task.
     */
    public void stop() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }

    /**
     * Broadcasts a random message from the config to all players.
     */
    private void broadcastRandomMessage() {
        List<String> messages = plugin.getConfig().getStringList("engagement.messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String message = messages.get(random.nextInt(messages.size()));
        
        // Translate color codes (just in case & is used, though § is in config)
        String formattedMessage = ChatColor.translateAlternateColorCodes('&', message);
        
        Bukkit.broadcastMessage(formattedMessage);
    }
}
