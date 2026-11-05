package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonSignComponentMigrationTest {
    @Test
    void usesAdventureComponentsForDungeonSigns() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonBuilder.java"));

        assertTrue(source.contains("sign.getSide(Side.FRONT).line(0, Component.text("));
        assertTrue(source.contains("ruleSign.getSide(Side.FRONT).line(0, Component.text("));
        assertTrue(source.contains("TextDecoration.BOLD"));
        assertTrue(source.contains("NamedTextColor.DARK_RED"));
        assertTrue(source.contains("NamedTextColor.DARK_GRAY"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains(".setLine("));
        assertFalse(source.contains("sign.line("));
    }
}
