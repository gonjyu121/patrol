package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeAnnouncementRotationTest {
    @Test
    void rotatesDistinctMessagesWithoutConsecutiveRepeats() {
        YouTubeAnnouncementRotation rotation = new YouTubeAnnouncementRotation();
        List<String> messages = List.of("案内A", "案内A", "案内B", "案内C");

        assertEquals("案内A", rotation.next(messages));
        assertEquals("案内B", rotation.next(messages));
        assertEquals("案内C", rotation.next(messages));
        assertEquals("案内A", rotation.next(messages));
    }

    @Test
    void ignoresBlankMessagesAndHandlesEmptyConfiguration() {
        YouTubeAnnouncementRotation rotation = new YouTubeAnnouncementRotation();
        assertEquals("案内", rotation.next(List.of(" ", "案内")));
        assertNull(rotation.next(List.of()));
    }

    @Test
    void defaultConfigurationPrioritizesParticipationAndWelcomesEachNewStream() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> messages = config.getStringList("youtube.idle_announcement.messages");
            assertEquals(90L, config.getLong("youtube.idle_announcement.interval_minutes"));
            assertEquals(6, messages.size());
            assertTrue(messages.subList(0, 4).stream().allMatch(message ->
                    message.contains("参加") || message.contains("入って")));
            assertTrue(messages.get(4).contains("双方向"));
            assertTrue(messages.get(5).contains("コメント"));
            assertTrue(config.getBoolean("youtube.stream_start_announcement.enabled"));
            assertEquals(90L, config.getLong("youtube.stream_start_announcement.delay_seconds"));
            assertTrue(config.getString("youtube.stream_start_announcement.message", "")
                    .contains("ご自由にご参加"));
        }
    }

    @Test
    void migratesOnlyTheLegacyThreeHourIdleInterval() {
        assertEquals(90L, YouTubeManager.resolveIdleAnnouncementMinutes(180L));
        assertEquals(60L, YouTubeManager.resolveIdleAnnouncementMinutes(60L));
        assertEquals(240L, YouTubeManager.resolveIdleAnnouncementMinutes(240L));
        assertEquals(1L, YouTubeManager.resolveIdleAnnouncementMinutes(0L));
    }
}
