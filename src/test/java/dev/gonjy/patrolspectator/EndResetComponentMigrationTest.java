package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndResetComponentMigrationTest {
    @Test
    void usesAdventureComponentsForResetMessages() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/EndResetManager.java"));

        assertTrue(source.contains("CameraMessageRouter.send(plugin, Component.text("));
        assertTrue(source.contains("p.sendMessage(Component.text("));
        assertTrue(source.contains("NamedTextColor.YELLOW"));
        assertTrue(source.contains("NamedTextColor.RED"));
        assertTrue(source.contains("NamedTextColor.GREEN"));
        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("ChatColor."));
    }
}
