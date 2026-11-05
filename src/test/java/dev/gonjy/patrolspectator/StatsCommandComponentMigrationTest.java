package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsCommandComponentMigrationTest {
    @Test
    void usesAdventureComponentsForStatsDisplay() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/StatsCommand.java"));

        assertTrue(source.contains("Component.text("));
        assertTrue(source.contains("NamedTextColor.YELLOW"));
        assertTrue(source.contains("LEGACY_SECTION.deserialize("));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
    }
}
