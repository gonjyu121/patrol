package dev.gonjy.patrolspectator;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Simple Discord Webhook Client.
 * Sends messages to a configured Discord Webhook URL.
 */
public class DiscordWebhookClient {

    private final JavaPlugin plugin;
    private String webhookUrl;
    private boolean enabled;

    public DiscordWebhookClient(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.webhookUrl = plugin.getConfig().getString("discord.webhook_url", "");

        if (enabled && (webhookUrl == null || webhookUrl.isEmpty())) {
            plugin.getLogger().warning("[Discord] Enabled but no Webhook URL provided. Disabling.");
            this.enabled = false;
        }
    }

    /**
     * Sends a message to Discord asynchronously.
     *
     * @param content The message content.
     */
    public void send(String content) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = java.net.URI.create(webhookUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "PatrolSpectatorPlugin");
                connection.setDoOutput(true);

                // Simple JSON payload: {"content": "message"}
                // Escaping quotes and backslashes is minimal here for simplicity,
                // but for robust production use, a JSON library is recommended.
                // Since we already have Google HTTP Client / Jackson from YouTube, we could use
                // that,
                // but to keep this class independent and lightweight, we'll do simple string
                // formatting
                // assuming the content is relatively safe (system messages).
                String jsonPayload = "{\"content\": \"" + escapeJson(content) + "\"}";

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("[Discord] Failed to send webhook. Response Code: " + responseCode);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Discord] Error sending webhook: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
