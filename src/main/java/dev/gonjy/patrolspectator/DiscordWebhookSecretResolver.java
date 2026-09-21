package dev.gonjy.patrolspectator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class DiscordWebhookSecretResolver {
    static final String RESOURCE_NAME = "discord-secret.properties";
    static final String PROPERTY_NAME = "discord.webhook_url";

    private DiscordWebhookSecretResolver() {
    }

    static String resolve(String configuredUrl, ClassLoader classLoader) {
        Properties properties = new Properties();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_NAME)) {
            if (input != null) {
                properties.load(input);
                String embeddedUrl = normalize(properties.getProperty(PROPERTY_NAME));
                if (embeddedUrl != null) {
                    return embeddedUrl;
                }
            }
        } catch (IOException ignored) {
            // Fall back without ever including secret contents in logs.
        }
        return normalize(configuredUrl);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
