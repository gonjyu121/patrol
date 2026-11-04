package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiscordWebhookUriMigrationTest {
    @Test
    void createsWebhookUrlsThroughUri() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/dev/gonjy/patrolspectator/DiscordWebhookClient.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("java.net.URI.create(webhookUrl).toURL()"));
        assertFalse(source.contains("new java.net.URL(webhookUrl)"));
    }
}
