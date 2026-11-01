package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeInitialChatReplyTest {
    @Test
    void acceptsRecentCommentFromInitialPage() {
        long connectedAt = 1_000_000L;
        YouTubeApiClient.LiveChatMessage message = messageAt(
                connectedAt - Duration.ofMinutes(2).toMillis());

        assertTrue(YouTubeManager.isRecentInitialMessage(message, connectedAt, connectedAt + 1_000L));
    }

    @Test
    void rejectsOldMissingAndImplausiblyFutureComments() {
        long connectedAt = 1_000_000L;
        assertFalse(YouTubeManager.isRecentInitialMessage(
                messageAt(connectedAt - Duration.ofMinutes(6).toMillis()), connectedAt, connectedAt));
        assertFalse(YouTubeManager.isRecentInitialMessage(
                new YouTubeApiClient.LiveChatMessage("id", "channel", "name", "text", false, null),
                connectedAt, connectedAt));
        assertFalse(YouTubeManager.isRecentInitialMessage(
                messageAt(connectedAt + Duration.ofMinutes(2).toMillis()), connectedAt, connectedAt));
    }

    private YouTubeApiClient.LiveChatMessage messageAt(long publishedAt) {
        return new YouTubeApiClient.LiveChatMessage(
                "id", "channel", "name", "参加していい？", false, publishedAt);
    }
}
