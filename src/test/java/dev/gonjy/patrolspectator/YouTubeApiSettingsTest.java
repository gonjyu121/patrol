package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeApiSettingsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingSettingsRemainOptional() {
        YouTubeApiSettings settings = YouTubeApiSettings.load(tempDirectory, Map.of());
        assertFalse(settings.configured());
    }

    @Test
    void loadsExternalPropertiesFile() throws Exception {
        Files.writeString(tempDirectory.resolve(YouTubeApiSettings.FILE_NAME), """
                clientId=file-client
                clientSecret=file-secret
                refreshToken=file-refresh
                """);

        YouTubeApiSettings settings = YouTubeApiSettings.load(tempDirectory, Map.of());

        assertTrue(settings.configured());
        assertEquals("file-client", settings.clientId());
        assertEquals("file-secret", settings.clientSecret());
        assertEquals("file-refresh", settings.refreshToken());
    }

    @Test
    void environmentOverridesPropertiesFile() throws Exception {
        Files.writeString(tempDirectory.resolve(YouTubeApiSettings.FILE_NAME), """
                clientId=file-client
                clientSecret=file-secret
                refreshToken=file-refresh
                """);

        YouTubeApiSettings settings = YouTubeApiSettings.load(tempDirectory, Map.of(
                "YOUTUBE_CLIENT_ID", "env-client",
                "YOUTUBE_CLIENT_SECRET", "env-secret",
                "YOUTUBE_REFRESH_TOKEN", "env-refresh"));

        assertEquals("env-client", settings.clientId());
        assertEquals("env-secret", settings.clientSecret());
        assertEquals("env-refresh", settings.refreshToken());
    }
}
