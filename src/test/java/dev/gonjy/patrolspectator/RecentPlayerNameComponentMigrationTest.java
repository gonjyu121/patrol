package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentPlayerNameComponentMigrationTest {
    @Test
    void usesAdventureSerializersForPlayerNameSanitization() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/RecentPlayerLocationStore.java"));

        assertTrue(source.contains("PlainTextComponentSerializer.plainText()"));
        assertTrue(source.contains("LEGACY_SECTION.deserialize(name)"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor.stripColor"));
    }
}
