package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles periodic in-game announcements to encourage YouTube engagement.
 */
public class EngagementBroadcaster {
    private static final Pattern ALTERNATE_COLOR_CODE = Pattern.compile("(?i)&([0-9A-FK-ORX])");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    static final String SPAWN_RESET_NOTICE =
            "§e[土地について] §f初期スポーン周辺は、荒れ方によって鯖主が再生成する場合があります。"
                    + "建築を残したい場合は、初期スポーンから5チャンク以上離れてください。";

    private final JavaPlugin plugin;
    private final Supplier<String> youtubeWatchUrl;
    private final Random random = new Random();
    private BukkitTask broadcastTask;
    private BukkitTask participantGuideTask;

    public EngagementBroadcaster(JavaPlugin plugin) {
        this(plugin, () -> null);
    }

    EngagementBroadcaster(JavaPlugin plugin, Supplier<String> youtubeWatchUrl) {
        this.plugin = plugin;
        this.youtubeWatchUrl = youtubeWatchUrl;
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
        
        CameraMessageRouter.send(plugin, deserializeLegacyColors(message));
    }

    /** Sends compact command and channel guidance to every online player, including the stream camera. */
    private void sendParticipantGuidance() {
        List<String> messages = plugin.getConfig().getStringList("participant_guidance.messages");
        if (messages != null) {
            for (String message : messages) {
                String resolved = resolveYouTubeUrl(message, youtubeWatchUrl.get());
                if (resolved == null) continue;
                ParticipantMessageRouter.send(plugin, deserializeLegacyColors(resolved));
            }
        }
        ParticipantMessageRouter.send(plugin, deserializeLegacyColors(SPAWN_RESET_NOTICE));
    }

    static Component deserializeLegacyColors(String message) {
        Component result = Component.empty();
        Matcher urls = URL.matcher(message);
        int cursor = 0;
        while (urls.find()) {
            result = result.append(deserializeNonUrlSegment(message.substring(cursor, urls.start())))
                    .append(Component.text(urls.group()));
            cursor = urls.end();
        }
        return result.append(deserializeNonUrlSegment(message.substring(cursor)));
    }

    private static Component deserializeNonUrlSegment(String message) {
        String normalized = ALTERNATE_COLOR_CODE.matcher(message).replaceAll("§$1");
        return LEGACY_SECTION.deserialize(normalized);
    }

    static String resolveYouTubeUrl(String message, String watchUrl) {
        if (message == null) return null;
        if (!message.contains("{youtube_url}")) return message;
        if (watchUrl == null || watchUrl.isBlank()) return null;
        return message.replace("{youtube_url}", watchUrl);
    }
}
