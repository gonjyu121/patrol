package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerDiagnosticsPluginMetaMigrationTest {
    @Test
    void readsPluginVersionsFromPaperPluginMeta() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/ServerDiagnosticsService.java"));

        assertTrue(source.contains("plugin.getPluginMeta().getVersion()"));
        assertTrue(source.contains("dependency.getPluginMeta().getVersion()"));
        assertFalse(source.contains("getDescription().getVersion()"));
    }
}
