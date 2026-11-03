package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockInteractionPerformanceMigrationTest {
    @Test
    void migratesLegacyCameraHeavyDefaultsOnce() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("patrol.mirrorObservedContainers", true);
        config.set("patrol.trackingUpdateIntervalTicks", 2);
        config.set("performance.disableLoadPause", true);

        assertTrue(BedrockInteractionPerformanceMigration.apply(config));
        assertFalse(config.getBoolean("patrol.mirrorObservedContainers"));
        assertEquals(4, config.getInt("patrol.trackingUpdateIntervalTicks"));
        assertFalse(config.getBoolean("performance.disableLoadPause"));
        assertTrue(config.getBoolean(BedrockInteractionPerformanceMigration.MARKER));

        config.set("patrol.trackingUpdateIntervalTicks", 6);
        assertFalse(BedrockInteractionPerformanceMigration.apply(config));
        assertEquals(6, config.getInt("patrol.trackingUpdateIntervalTicks"));
    }

    @Test
    void preservesExistingLightweightCustomValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("patrol.mirrorObservedContainers", false);
        config.set("patrol.trackingUpdateIntervalTicks", 6);
        config.set("performance.disableLoadPause", false);

        assertFalse(BedrockInteractionPerformanceMigration.apply(config));
        assertFalse(config.getBoolean("patrol.mirrorObservedContainers"));
        assertEquals(6, config.getInt("patrol.trackingUpdateIntervalTicks"));
        assertFalse(config.getBoolean("performance.disableLoadPause"));
        assertTrue(config.getBoolean(BedrockInteractionPerformanceMigration.MARKER));
    }
}
