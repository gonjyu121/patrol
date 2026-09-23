package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantMessageRouterTest {
    @Test
    void guidanceIsSentToCameraAndRegularParticipant() {
        ServerMock server = MockBukkit.mock();
        try {
            JavaPlugin plugin = MockBukkit.createMockPlugin("ParticipantMessageRouterTest");
            PlayerMock camera = server.addPlayer("OtouGame");
            PlayerMock participant = server.addPlayer("OtherPlayer");

            ParticipantMessageRouter.send(plugin, "定期案内");

            assertEquals(Component.text("定期案内"), camera.nextComponentMessage());
            assertEquals(Component.text("定期案内"), participant.nextComponentMessage());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void defaultGuidanceContainsSubscriptionPublicCommandsAndSpawnResetNotice() throws Exception {
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
            assertTrue(combined.contains("鯖主が再生成"));
            assertTrue(combined.contains("5チャンク以上"));
        }
    }
}
