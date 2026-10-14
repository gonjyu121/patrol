package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordWebhookClientTest {
    private static final String URL = "https://www.youtube.com/watch?v=video-123";

    @Test
    void appendsCurrentStreamUrlToNotificationsOnlyOnce() {
        assertEquals("イベント開始\n📺 配信中: " + URL,
                DiscordWebhookClient.appendStreamUrl("イベント開始", URL));
        assertEquals("イベント開始\n📺 配信中: " + URL,
                DiscordWebhookClient.appendStreamUrl("イベント開始\n📺 配信中: " + URL, URL));
        assertEquals("イベント開始", DiscordWebhookClient.appendStreamUrl("イベント開始", null));
    }

    @Test
    void doesNotExceedDiscordMessageLimit() {
        String content = "x".repeat(1_990);
        assertEquals(content, DiscordWebhookClient.appendStreamUrl(content, URL));
    }
}
