package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeStreamStartStateTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAnnouncedBroadcastAcrossRestart() throws Exception {
        YouTubeStreamStartState state = new YouTubeStreamStartState(temporaryDirectory);
        assertTrue(state.needsAnnouncement("video-one"));

        state.markAnnounced("video-one");
        assertFalse(state.needsAnnouncement("video-one"));
        assertTrue(state.needsAnnouncement("video-two"));

        YouTubeStreamStartState reloaded = new YouTubeStreamStartState(temporaryDirectory);
        assertFalse(reloaded.needsAnnouncement("video-one"));
        assertTrue(reloaded.needsAnnouncement("video-two"));
    }

    @Test
    void ignoresMissingVideoId() {
        YouTubeStreamStartState state = new YouTubeStreamStartState(temporaryDirectory);
        assertFalse(state.needsAnnouncement(null));
        assertFalse(state.needsAnnouncement(" "));
    }
}
