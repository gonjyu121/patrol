package dev.gonjy.patrolspectator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listens to public Minecraft chat and forwards it to YouTube.
 */
@SuppressWarnings("deprecation")
public class YouTubeChatListener implements Listener {
    private final YouTubeManager youTubeManager;

    public YouTubeChatListener(YouTubeManager youTubeManager) {
        this.youTubeManager = youTubeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        youTubeManager.sendPlayerMessage(event.getPlayer().getName(), event.getMessage());
    }
}
