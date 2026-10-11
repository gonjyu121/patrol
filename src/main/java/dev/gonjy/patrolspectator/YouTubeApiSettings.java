package dev.gonjy.patrolspectator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

record YouTubeApiSettings(String clientId, String clientSecret, String refreshToken) {
    static final String FILE_NAME = "youtube-api.properties";

    static YouTubeApiSettings load(Path pluginDataDirectory) {
        return load(pluginDataDirectory, System.getenv());
    }

    static YouTubeApiSettings load(Path pluginDataDirectory, Map<String, String> environment) {
        Properties properties = new Properties();
        Path path = pluginDataDirectory.resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
                return new YouTubeApiSettings(null, null, null);
            }
        }
        return new YouTubeApiSettings(
                firstNonBlank(environment.get("YOUTUBE_CLIENT_ID"), properties.getProperty("clientId")),
                firstNonBlank(environment.get("YOUTUBE_CLIENT_SECRET"), properties.getProperty("clientSecret")),
                firstNonBlank(environment.get("YOUTUBE_REFRESH_TOKEN"), properties.getProperty("refreshToken")));
    }

    boolean configured() {
        return clientId != null && clientSecret != null && refreshToken != null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
