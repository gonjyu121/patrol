package dev.gonjy.patrolspectator;

import org.bukkit.plugin.java.JavaPlugin;

public class DiscordWebhookClient {
    private final JavaPlugin plugin;
    private final java.util.concurrent.BlockingQueue<String> messageQueue = new java.util.concurrent.LinkedBlockingQueue<>(
            1);
    private volatile boolean running = true;
    private final Thread workerThread;
    private String webhookUrl;
    private boolean enabled = true;

    public DiscordWebhookClient(JavaPlugin plugin) {
        this.plugin = plugin;
        this.workerThread = new Thread(this::processQueue, "DiscordWebhook-Worker");
        this.workerThread.start();
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", true);
        this.webhookUrl = plugin.getConfig().getString("discord.webhook_url");
        if (this.enabled && (this.webhookUrl == null || this.webhookUrl.isEmpty())) {
            plugin.getLogger().warning("Discord Webhook URL is not configured in config.yml");
        }
    }

    public void send(String content) {
        if (!enabled || this.webhookUrl == null || this.webhookUrl.isEmpty())
            return;

        // キューが一杯の場合は新しいメッセージを破棄（低負荷設定）
        if (!messageQueue.offer(content)) {
            // オプション：ログに出しても良いが、低スペック用なので静かに破棄
        }
    }

    public void shutdown() {
        running = false;
        workerThread.interrupt();
    }

    private void processQueue() {
        while (running) {
            try {
                String content = messageQueue.take();
                sendInternal(content);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error in Discord webhook worker", e);
            }
        }
    }

    private void sendInternal(String content) {
        try {
            java.net.URL url = new java.net.URL(webhookUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // Using Gson if available, or manual JSON construction if simple
            // Paper/Spigot usually includes Gson.
            // Manual JSON construction to avoid Gson dependency issues
            String escapedContent = content.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            String jsonPayload = "{\"content\": \"" + escapedContent + "\"}";

            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                plugin.getLogger().warning("Failed to send Discord webhook. Code: " + responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect/send to Discord: " + e.getMessage());
        }
    }
}
