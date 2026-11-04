package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperEventComponentMigrationTest {
    @Test
    void usesAdventureComponentsForPaperEventsAndBroadcasts() throws Exception {
        String gameModeEnforcer = source("GameModeEnforcer.java");
        String patrolManager = source("PatrolManager.java");
        String youtubeManager = source("YouTubeManager.java");

        assertTrue(gameModeEnforcer.contains("e.deathMessage(null)"));
        assertFalse(gameModeEnforcer.contains("e.setDeathMessage(null)"));
        assertTrue(patrolManager.contains("serialize(e.reason())"));
        assertFalse(patrolManager.contains("e.getReason()"));
        assertTrue(youtubeManager.contains("broadcast(Component.text(formatted))"));
        assertFalse(youtubeManager.contains("broadcastMessage(formatted)"));
    }

    private String source(String fileName) throws Exception {
        return Files.readString(
                Path.of("src/main/java/dev/gonjy/patrolspectator", fileName),
                StandardCharsets.UTF_8);
    }
}
