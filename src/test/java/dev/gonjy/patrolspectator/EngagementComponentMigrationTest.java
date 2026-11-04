package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EngagementComponentMigrationTest {
    @Test
    void usesAdventureForMilestoneAndRankNotifications() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/dev/gonjy/patrolspectator/EngagementSystem.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("player.showTitle(Title.title("));
        assertTrue(source.contains("CameraMessageRouter.send(plugin, announcement)"));
        assertFalse(source.contains("player.sendTitle("));
        assertFalse(source.contains("ChatColor"));
    }
}
