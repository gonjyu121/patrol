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
    private BukkitTask participantGuideTask;

    public EngagementBroadcaster(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the periodic broadcast task based on config.
     */
    public void start() {
        stop();

        startCameraEngagement();
        startParticipantGuidance();
    }

    private void startCameraEngagement() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("engagement.enabled", true)) return;

        int intervalMinutes = config.getInt("engagement.interval_minutes", 15);
        if (intervalMinutes <= 0) return;

        long ticks = intervalMinutes * 60L * 20L;

        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastRandomMessage, ticks, ticks);
        plugin.getLogger().info("[Engagement] Periodic broadcasts started every " + intervalMinutes + " minutes.");
    }

    private void startParticipantGuidance() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("participant_guidance.enabled", true)) return;

        int intervalMinutes = config.getInt("participant_guidance.interval_minutes", 30);
        if (intervalMinutes <= 0) return;

        long ticks = intervalMinutes * 60L * 20L;
        participantGuideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendParticipantGuidance, ticks, ticks);
        plugin.getLogger().info("[ParticipantGuide] Participant guidance started every "
                + intervalMinutes + " minutes.");
    }

    /**
     * Stops the periodic broadcast task.
     */
    public void stop() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        if (participantGuideTask != null) {
            participantGuideTask.cancel();
            participantGuideTask = null;
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
        
        dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin, formattedMessage);
    }

    /** Sends compact command and channel guidance to every online player, including the stream camera. */
    private void sendParticipantGuidance() {
        List<String> messages = plugin.getConfig().getStringList("participant_guidance.messages");
        if (messages == null || messages.isEmpty()) return;

        for (String message : messages) {
            String formatted = ChatColor.translateAlternateColorCodes('&', message);
            ParticipantMessageRouter.send(plugin, formatted);
        }
    }
}
