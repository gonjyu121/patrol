package dev.gonjy.patrolspectator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens to Minecraft chat and forwards it to YouTube.
 * (Temporarily disabled)
 */
@SuppressWarnings({ "unused", "deprecation" })
public class YouTubeChatListener implements Listener {

    private final JavaPlugin plugin;
    private final YouTubeManager youTubeManager;

    public YouTubeChatListener(JavaPlugin plugin, YouTubeManager youTubeManager) {
        this.plugin = plugin;
        this.youTubeManager = youTubeManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Disabled
    }
}
