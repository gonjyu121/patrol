package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YouTubeApiClientTest {
    @Test
    void extractsLiveChatIdFromActiveBroadcast() {
        assertEquals("chat-123", YouTubeApiClient.parseLiveChatId("""
                {"items":[{"snippet":{"liveChatId":"chat-123"}}]}
                """));
    }

    @Test
    void reportsNoChatForEmptyOrMalformedResponses() {
        assertNull(YouTubeApiClient.parseLiveChatId("{\"items\":[]}"));
        assertNull(YouTubeApiClient.parseLiveChatId("not-json"));
    }

    @Test
    void doesNotGuessWhenMultipleStreamsAreActive() {
        assertNull(YouTubeApiClient.parseLiveChatId("""
                {"items":[
                  {"snippet":{"liveChatId":"chat-1"}},
                  {"snippet":{"liveChatId":"chat-2"}}
                ]}
                """));
    }
}
