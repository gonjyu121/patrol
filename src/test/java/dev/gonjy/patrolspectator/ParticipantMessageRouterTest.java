package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantMessageRouterTest {
    @Test
    void exactJavaCameraIsExcludedButBedrockPlayAccountReceivesGuidance() {
        assertFalse(ParticipantMessageRouter.isParticipantName("OtouGame", "OtouGame"));
        assertFalse(ParticipantMessageRouter.isParticipantName("OtouGame", "otougame"));
        assertTrue(ParticipantMessageRouter.isParticipantName("OtouGame", ".OtouGame"));
        assertTrue(ParticipantMessageRouter.isParticipantName("OtouGame", "OtherPlayer"));
    }

    @Test
    void defaultGuidanceContainsSubscriptionAndPublicCommands() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(config.getBoolean("participant_guidance.enabled"));
            assertEquals(30, config.getInt("participant_guidance.interval_minutes"));
            List<String> messages = config.getStringList("participant_guidance.messages");
            String combined = String.join(" ", messages);
            assertTrue(combined.contains("チャンネル登録"));
            assertTrue(combined.contains("/stats"));
            assertTrue(combined.contains("/patrol rescue"));
            assertTrue(combined.contains("/patrol invite"));
            assertFalse(combined.contains("world"));
        }
    }
}
