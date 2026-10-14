package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
