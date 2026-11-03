package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.FileConfiguration;

/** Moves legacy camera-heavy defaults to settings that prioritize player interaction. */
final class BedrockInteractionPerformanceMigration {
    static final String MARKER = "config_migrations.bedrock_interaction_v1_9_164";

    private BedrockInteractionPerformanceMigration() {
    }

    static boolean apply(FileConfiguration config) {
        if (config.getBoolean(MARKER, false)) return false;

        boolean changed = false;
        if (config.getBoolean("patrol.mirrorObservedContainers", true)) {
            config.set("patrol.mirrorObservedContainers", false);
            changed = true;
        }
        if (config.getInt("patrol.trackingUpdateIntervalTicks", 2) == 2) {
            config.set("patrol.trackingUpdateIntervalTicks", 4);
            changed = true;
        }
        if (config.getBoolean("performance.disableLoadPause", true)) {
            config.set("performance.disableLoadPause", false);
            changed = true;
        }

        config.set(MARKER, true);
        return changed;
    }
}
