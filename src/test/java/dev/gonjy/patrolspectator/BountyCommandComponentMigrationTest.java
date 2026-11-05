package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyCommandComponentMigrationTest {
    @Test
    void usesAdventureComponentsForCommandMessages() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/BountyCommand.java"));

        assertTrue(source.contains("sender.sendMessage(Component.text("));
        assertTrue(source.contains("NamedTextColor.GOLD"));
        assertTrue(source.contains("NamedTextColor.AQUA"));
        assertTrue(source.contains(".append(Component.text("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
