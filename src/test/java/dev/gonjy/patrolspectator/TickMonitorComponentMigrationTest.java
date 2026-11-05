package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickMonitorComponentMigrationTest {
    @Test
    void usesAdventureComponentsForLoadNotifications() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/TickMonitor.java"));

        assertTrue(source.contains("Component.text("));
        assertTrue(source.contains("NamedTextColor.RED"));
        assertTrue(source.contains("NamedTextColor.GREEN"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
    }
}
