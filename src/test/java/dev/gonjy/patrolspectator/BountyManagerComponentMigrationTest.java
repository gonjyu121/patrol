package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyManagerComponentMigrationTest {
    @Test
    void usesAdventureComponentsForBountyAnnouncements() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/BountyManager.java"));

        assertTrue(source.contains("CameraMessageRouter.send(plugin, Component.text("));
        assertTrue(source.contains("killer.sendMessage(Component.text("));
        assertTrue(source.contains("NamedTextColor.DARK_RED"));
        assertTrue(source.contains("NamedTextColor.GOLD"));
        assertTrue(source.contains(".append(Component.text("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
