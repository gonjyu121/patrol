package dev.gonjy.patrolspectator;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens to public Minecraft chat and forwards it to YouTube.
 */
public class YouTubeChatListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private final YouTubeManager youTubeManager;

    public YouTubeChatListener(YouTubeManager youTubeManager) {
        this.youTubeManager = youTubeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        youTubeManager.sendPlayerMessage(event.getPlayer().getName(), PLAIN_TEXT.serialize(event.message()));
    }
}
