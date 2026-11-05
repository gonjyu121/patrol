package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrapRunnerComponentMigrationTest {
    @Test
    void usesAdventureComponentsForTrapMessagesAndMobNames() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/TrapRunner.java"));

        assertTrue(source.contains("p.sendMessage(Component.text("));
        assertTrue(source.contains("mob.customName(Component.text("));
        assertTrue(source.contains("NamedTextColor.DARK_PURPLE"));
        assertTrue(source.contains("NamedTextColor.DARK_RED"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("setCustomName("));
    }
}
