package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeChatListenerMigrationTest {
    @Test
    void forwardsAdventureChatAsPlainText() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/YouTubeChatListener.java"));

        assertTrue(source.contains("onPlayerChat(AsyncChatEvent event)"));
        assertTrue(source.contains("PLAIN_TEXT.serialize(event.message())"));
        assertTrue(source.contains("priority = EventPriority.MONITOR, ignoreCancelled = true"));
        assertFalse(source.contains("AsyncPlayerChatEvent"));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
