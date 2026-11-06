package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonListenerComponentMigrationTest {
    @Test
    void usesAdventureComponentsForMessagesBossNamesAndTitles() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonListener.java"));

        assertTrue(source.contains("player.sendMessage(Component.text(message, color))"));
        assertTrue(source.contains("entity.customName()"));
        assertTrue(source.contains("player.showTitle(Title.title("));
        assertTrue(source.contains("Title.Times.times("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("getCustomName()"));
        assertFalse(source.contains(".sendTitle("));
    }
}
