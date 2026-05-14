package dev.gonjy.patrolspectator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * GitHub Gistを使用してランキングデータを同期するクラス。
 * サーバー移行時やマルチサーバー環境でのデータ継続を目的としています。
 */
public class GistSyncManager {
    private final PatrolSpectatorPlugin plugin;
    private final Logger log;
    private final String token;
    private final String gistId;
    private final String fileName;
    private final HttpClient httpClient;

    public GistSyncManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.token = plugin.getConfig().getString("github.token", "");
        this.gistId = plugin.getConfig().getString("github.gistId", "");
        this.fileName = plugin.getConfig().getString("github.fileName", "player_stats.yml");
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return plugin.getConfig().getBoolean("github.enabled", false) 
                && !token.isEmpty() && !gistId.isEmpty();
    }

    /**
     * GitHub Gistからデータをダウンロードしてローカルファイルを上書きします。
     * 同期的に実行されるため、起動時のメインスレッドまたは非同期タスク内から呼んでください。
     */
    public void pull() {
        if (!isConfigured()) return;

        log.info("[CloudSync] GitHubからデータを取得中... (Gist: " + gistId + ")");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/gists/" + gistId))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "PatrolSpectatorPlugin")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warning("[CloudSync] プル失敗 (HTTP " + response.statusCode() + "): " + response.body());
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject files = json.getAsJsonObject("files");
            if (files != null && files.has(fileName)) {
                String content = files.getAsJsonObject(fileName).get("content").getAsString();
                File localFile = new File(plugin.getDataFolder(), fileName);
                Files.writeString(localFile.toPath(), content, StandardCharsets.UTF_8);
                log.info("[CloudSync] GitHubからデータを正常に読み込みました。");
            } else {
                log.warning("[CloudSync] Gist内に指定されたファイル(" + fileName + ")が見つかりませんでした。新しく作成されるまで待機します。");
            }
        } catch (Exception e) {
            log.severe("[CloudSync] プル中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * ローカルファイルのデータをGitHub Gistへアップロードします。
     */
    public void push() {
        if (!isConfigured()) return;

        log.info("[CloudSync] GitHubへデータを保存中...");
        try {
            File localFile = new File(plugin.getDataFolder(), fileName);
            if (!localFile.exists()) {
                log.warning("[CloudSync] ローカルファイルが見つかりません: " + fileName);
                return;
            }

            String content = Files.readString(localFile.toPath(), StandardCharsets.UTF_8);

            // JSON構築
            JsonObject root = new JsonObject();
            JsonObject files = new JsonObject();
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("content", content);
            files.add(fileName, fileObj);
            root.add("files", files);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/gists/" + gistId))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "PatrolSpectatorPlugin")
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[CloudSync] GitHubへの保存が完了しました。");
            } else {
                log.warning("[CloudSync] プッシュ失敗 (HTTP " + response.statusCode() + "): " + response.body());
            }
        } catch (Exception e) {
            log.severe("[CloudSync] プッシュ中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * 非同期で保存を実行します。
     */
    public void pushAsync() {
        if (!isConfigured()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::push);
    }
}
