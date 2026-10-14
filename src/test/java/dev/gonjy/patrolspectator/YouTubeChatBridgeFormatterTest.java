package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeChatBridgeFormatterTest {
    @Test
    void formatsMinecraftAndYouTubeMessagesWithoutControlCodes() {
        assertEquals("[マイクラ] Player: hello world",
                YouTubeChatBridgeFormatter.toYouTube("§aPlayer", "hello\nworld"));
        assertEquals("§c[YouTube] §fViewer§7: §fこんにちは",
                YouTubeChatBridgeFormatter.toMinecraft("Viewer", "こんにちは"));
        assertNull(YouTubeChatBridgeFormatter.toYouTube("Player", "   "));
        assertEquals("[マイクラ] 案内です", YouTubeChatBridgeFormatter.announcement("案内です"));
    }

    @Test
    void suppressesOnlyPluginGeneratedOwnerMessages() {
        assertTrue(YouTubeChatBridgeFormatter.isPluginGenerated("[マイクラ] Player: hello", true));
        assertTrue(YouTubeChatBridgeFormatter.isPluginGenerated("[Minecraft] Player: hello", true));
        assertTrue(YouTubeChatBridgeFormatter.isPluginGenerated("[Patrol] welcome", true));
        assertFalse(YouTubeChatBridgeFormatter.isPluginGenerated("[マイクラ] Viewer text", false));
        assertFalse(YouTubeChatBridgeFormatter.isPluginGenerated("owner manual message", true));
    }
}
