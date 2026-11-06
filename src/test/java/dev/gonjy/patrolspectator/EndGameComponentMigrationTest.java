package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndGameComponentMigrationTest {
    @Test
    void usesAdventureComponentsForDragonMessagesNamesAndBossBar() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/EndGameManager.java"));

        assertTrue(source.contains("BossBar.bossBar("));
        assertTrue(source.contains("player.showBossBar(bossBar)"));
        assertTrue(source.contains("player.hideBossBar(bossBar)"));
        assertTrue(source.contains("dragon.customName(Component.text("));
        assertTrue(source.contains("CameraMessageRouter.send(plugin, Component.text("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("Bukkit.createBossBar("));
        assertFalse(source.contains("setCustomName("));
        assertFalse(source.contains("getCustomName("));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
