package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementBroadcasterComponentMigrationTest {
    @Test
    void supportsBothLegacyColorMarkersWithoutChangingUrlAmpersands() {
        Component component = EngagementBroadcaster.deserializeLegacyColors(
                "&e案内 §fhttps://example.test/watch?a=1&b=2");
        String serialized = LegacyComponentSerializer.legacySection().serialize(component);

        assertTrue(serialized.contains("§e案内"));
        assertTrue(serialized.contains("https://example.test/watch?a=1&b=2"));
        assertFalse(serialized.contains("watch?a=1§b=2"));
    }

    @Test
    void routesAnnouncementsAsAdventureComponents() throws Exception {
        String broadcaster = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/EngagementBroadcaster.java"));
        String router = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/ParticipantMessageRouter.java"));

        assertTrue(broadcaster.contains("deserializeLegacyColors"));
        assertTrue(router.contains("send(JavaPlugin plugin, Component message)"));
        assertFalse(broadcaster.contains("org.bukkit.ChatColor"));
        assertFalse(broadcaster.contains("ChatColor."));
    }
}
