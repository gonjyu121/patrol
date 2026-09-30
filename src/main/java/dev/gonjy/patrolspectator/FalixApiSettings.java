package dev.gonjy.patrolspectator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record FalixApiSettings(String apiKey, String serverId, String geyserAllocationNote) {
    static final String FILE_NAME = "falix-api.properties";

    static FalixApiSettings load(Path pluginDataDirectory) {
        Properties properties = new Properties();
        Path path = pluginDataDirectory.resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
                return new FalixApiSettings(null, null, "Geyser");
            }
        }

        String apiKey = firstNonBlank(System.getenv("FALIX_API_KEY"), properties.getProperty("apiKey"));
        String serverId = firstNonBlank(System.getenv("FALIX_SERVER_ID"), properties.getProperty("serverId"));
        String note = firstNonBlank(System.getenv("FALIX_GEYSER_ALLOCATION_NOTE"),
                properties.getProperty("geyserAllocationNote"), "Geyser");
        return new FalixApiSettings(apiKey, serverId, note);
    }

    boolean configured() {
        return apiKey != null && serverId != null && serverId.matches("[A-Za-z0-9_-]+");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
