package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonCommandComponentMigrationTest {
    @Test
    void usesAdventureComponentsForCommandResponsesAndHelp() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonCommand.java"));

        assertTrue(source.contains("sender.sendMessage(Component.text(message, color))"));
        assertTrue(source.contains("Component.text(command, NamedTextColor.GREEN)"));
        assertTrue(source.contains(".append(Component.text(\" - \" + description, NamedTextColor.GRAY))"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("§"));
    }
}
