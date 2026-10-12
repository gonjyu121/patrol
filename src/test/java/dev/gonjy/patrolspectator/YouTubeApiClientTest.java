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

    @Test
    void parsesOnlyTextChatMessagesAndContinuationData() {
        YouTubeApiClient.LiveChatPage page = YouTubeApiClient.parseLiveChatPage("""
                {
                  "nextPageToken":"next-1",
                  "pollingIntervalMillis":5000,
                  "items":[
                    {"id":"m1","snippet":{"type":"textMessageEvent","displayMessage":"参加していい？"},
                     "authorDetails":{"channelId":"c1","displayName":"viewer"}},
                    {"id":"m2","snippet":{"type":"newSponsorEvent","displayMessage":"joined"},
                     "authorDetails":{"channelId":"c2","displayName":"member"}}
                  ]
                }
                """);

        assertEquals("next-1", page.nextPageToken());
        assertEquals(5000L, page.pollingIntervalMillis());
        assertEquals(1, page.messages().size());
        assertEquals("c1", page.messages().getFirst().authorChannelId());
    }
}
