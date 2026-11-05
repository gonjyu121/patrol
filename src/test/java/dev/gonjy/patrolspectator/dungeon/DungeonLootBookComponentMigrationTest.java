package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonLootBookComponentMigrationTest {
    @Test
    void usesAdventureComponentsForJournalMetadataAndPages() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonLootSystem.java"));

        assertTrue(source.contains("meta.title(Component.text("));
        assertTrue(source.contains("meta.author(Component.text("));
        assertTrue(source.contains("meta.addPages(Component.text("));
        assertTrue(source.contains("NamedTextColor.DARK_GRAY"));
        assertTrue(source.contains("NamedTextColor.BLACK"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("meta.setTitle("));
        assertFalse(source.contains("meta.setAuthor("));
        assertFalse(source.contains("meta.addPage("));
    }
}
