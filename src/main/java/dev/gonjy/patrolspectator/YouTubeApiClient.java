package dev.gonjy.patrolspectator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class YouTubeApiClient {
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI ACTIVE_BROADCASTS_URI = URI.create(
            "https://www.googleapis.com/youtube/v3/liveBroadcasts"
                    + "?part=snippet&broadcastStatus=active&broadcastType=all&maxResults=5");

    private final YouTubeApiSettings settings;
    private final HttpClient httpClient;
    private String accessToken;
    private long accessTokenExpiresAt;

    YouTubeApiClient(YouTubeApiSettings settings) {
        this(settings, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    YouTubeApiClient(YouTubeApiSettings settings, HttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    synchronized String discoverActiveLiveChatId() throws IOException, InterruptedException, ApiException {
        String token = accessToken();
        HttpRequest request = HttpRequest.newBuilder(ACTIVE_BROADCASTS_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", "PatrolSpectatorPlugin")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            accessToken = null;
            throw new ApiException(401, "AUTH_ERROR");
        }
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), "API_ERROR");
        }
        List<String> liveChatIds = parseActiveLiveChatIds(response.body());
        if (liveChatIds.size() > 1) throw new ApiException(200, "MULTIPLE_ACTIVE_STREAMS");
        return liveChatIds.isEmpty() ? null : liveChatIds.getFirst();
    }

    private String accessToken() throws IOException, InterruptedException, ApiException {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) return accessToken;

        String body = "client_id=" + encode(settings.clientId())
                + "&client_secret=" + encode(settings.clientSecret())
                + "&refresh_token=" + encode(settings.refreshToken())
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), "AUTH_ERROR");
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String token = string(json, "access_token");
        if (token == null) throw new ApiException(200, "INVALID_TOKEN_RESPONSE");
        long expiresIn = json.has("expires_in") ? Math.max(60L, json.get("expires_in").getAsLong()) : 3600L;
        accessToken = token;
        accessTokenExpiresAt = System.currentTimeMillis() + Math.max(30L, expiresIn - 60L) * 1000L;
        return accessToken;
    }

    static String parseLiveChatId(String responseBody) {
        List<String> liveChatIds = parseActiveLiveChatIds(responseBody);
        return liveChatIds.size() == 1 ? liveChatIds.getFirst() : null;
    }

    private static List<String> parseActiveLiveChatIds(String responseBody) {
        List<String> liveChatIds = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) return liveChatIds;
            for (var item : items) {
                JsonObject snippet = item.getAsJsonObject().getAsJsonObject("snippet");
                String liveChatId = string(snippet, "liveChatId");
                if (liveChatId != null && !liveChatIds.contains(liveChatId)) liveChatIds.add(liveChatId);
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }
        return liveChatIds;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        String value = object.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class ApiException extends Exception {
        private final int statusCode;
        private final String category;

        ApiException(int statusCode, String category) {
            super(category + " (HTTP " + statusCode + ")");
            this.statusCode = statusCode;
            this.category = category;
        }

        int statusCode() { return statusCode; }
        String category() { return category; }
    }
}
