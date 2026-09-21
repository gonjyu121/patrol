package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DiscordWebhookSecretResolverTest {
    @Test
    void embeddedSecretTakesPriorityOverServerConfig() {
        ClassLoader loader = loaderWith("discord.webhook_url=https://local.invalid/hook\n");
        assertEquals("https://local.invalid/hook",
                DiscordWebhookSecretResolver.resolve("https://server.invalid/old", loader));
    }

    @Test
    void fallsBackToServerConfigWhenSecretIsNotEmbedded() {
        assertEquals("https://server.invalid/hook",
                DiscordWebhookSecretResolver.resolve("  https://server.invalid/hook  ", loaderWith(null)));
    }

    @Test
    void returnsNullWhenNeitherSourceIsConfigured() {
        assertNull(DiscordWebhookSecretResolver.resolve("  ", loaderWith(null)));
    }

    private static ClassLoader loaderWith(String contents) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (!DiscordWebhookSecretResolver.RESOURCE_NAME.equals(name) || contents == null) {
                    return null;
                }
                return new ByteArrayInputStream(contents.getBytes(StandardCharsets.ISO_8859_1));
            }
        };
    }
}
