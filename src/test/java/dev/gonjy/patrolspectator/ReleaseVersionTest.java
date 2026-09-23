package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {
    @Test
    void packagedPluginDescriptorHasReleaseVersion() {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals("1.9.130", descriptor.getString("version"));
            assertTrue(descriptor.getStringList("softdepend").contains("WorldEdit"));
            assertNull(descriptor.getString("commands.patrol.permission"));
            assertTrue(descriptor.getBoolean("permissions.patrol.rescue.default"));
            assertTrue(descriptor.getBoolean("permissions.patrol.teleport.default"));
        } catch (Exception e) {
            throw new AssertionError("Failed to read packaged plugin descriptor", e);
        }
    }
}
