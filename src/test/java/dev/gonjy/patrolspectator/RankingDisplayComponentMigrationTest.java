package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingDisplayComponentMigrationTest {
    @Test
    void usesAdventureComponentsForRankingMessagesAndTitles() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/RankingDisplaySystem.java"));

        assertTrue(source.contains("player.showTitle(Title.title("));
        assertTrue(source.contains("CameraMessageRouter.send(plugin, Component.text("));
        assertTrue(source.contains("getDisplayNameComponent"));
        assertTrue(source.contains("rankingLine("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains(".sendTitle("));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
