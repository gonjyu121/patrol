package dev.gonjy.patrolspectator;

import org.bukkit.plugin.java.JavaPlugin;

public class DiscordWebhookClient {
    private final JavaPlugin plugin;
    private final java.util.concurrent.BlockingQueue<String> messageQueue = new java.util.concurrent.LinkedBlockingQueue<>(
            16);
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

    /**
     * 指定したファイルをバックアップとして送信します。
     * (multipart/form-data を使用)
     */
    public void sendFile(java.io.File file, String content) {
        if (!enabled || this.webhookUrl == null || this.webhookUrl.isEmpty())
            return;

        // 非同期で実行
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendMultipartInternal(file, content);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send file to Discord: " + e.getMessage());
            }
        });
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
        // ... (existing code remains mostly same, just for context)
        try {
            java.net.URL url = new java.net.URL(webhookUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

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

    private void sendMultipartInternal(java.io.File file, String content) throws java.io.IOException {
        String boundary = "===" + System.currentTimeMillis() + "===";
        java.net.URL url = new java.net.URL(webhookUrl);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (java.io.OutputStream os = connection.getOutputStream();
             java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(os, "UTF-8"), true)) {

            // Content part
            if (content != null && !content.isEmpty()) {
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"content\"\r\n\r\n");
                writer.append(content).append("\r\n");
            }

            // File part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: application/octet-stream\r\n\r\n");
            writer.flush();

            // Write actual file bytes
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            os.flush();
            writer.append("\r\n");

            // End of multipart
            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            plugin.getLogger().warning("Failed to upload file to Discord. Code: " + responseCode);
        }
        connection.disconnect();
    }
}
