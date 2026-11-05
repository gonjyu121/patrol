package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonBossComponentMigrationTest {
    @Test
    void usesAdventureComponentsForBossAndLootNames() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonBossSystem.java"));

        assertTrue(source.contains("boss.customName(Component.text("));
        assertTrue(source.contains("meta.displayName(Component.text("));
        assertTrue(source.contains("NamedTextColor.GOLD"));
        assertTrue(source.contains("NamedTextColor.DARK_AQUA"));
        assertTrue(source.contains("NamedTextColor.GRAY"));
        assertTrue(source.contains("NamedTextColor.DARK_PURPLE"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
        assertFalse(source.contains("setCustomName("));
        assertFalse(source.contains("setDisplayName("));
    }
}
