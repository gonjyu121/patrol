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
    private static final String LIVE_CHAT_MESSAGES_URI =
            "https://www.googleapis.com/youtube/v3/liveChat/messages";

    private final YouTubeApiSettings settings;
    private final HttpClient httpClient;
    private final YouTubeQuotaBudget quotaBudget;
    private String accessToken;
    private long accessTokenExpiresAt;

    YouTubeApiClient(YouTubeApiSettings settings, YouTubeQuotaBudget quotaBudget) {
        this(settings, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), quotaBudget);
    }

    YouTubeApiClient(YouTubeApiSettings settings, HttpClient httpClient, YouTubeQuotaBudget quotaBudget) {
        this.settings = settings;
        this.httpClient = httpClient;
        this.quotaBudget = quotaBudget;
    }

    synchronized String discoverActiveLiveChatId() throws IOException, InterruptedException, ApiException {
        requireQuota(1, false);
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

    synchronized LiveChatPage listLiveChatMessages(String liveChatId, String pageToken)
            throws IOException, InterruptedException, ApiException {
        requireQuota(5, false);
        StringBuilder uri = new StringBuilder(LIVE_CHAT_MESSAGES_URI)
                .append("?part=id,snippet,authorDetails&maxResults=200&liveChatId=")
                .append(encode(liveChatId));
        if (pageToken != null && !pageToken.isBlank()) uri.append("&pageToken=").append(encode(pageToken));
        HttpRequest request = authorizedRequest(URI.create(uri.toString())).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        validateApiResponse(response.statusCode());
        return parseLiveChatPage(response.body());
    }

    synchronized void insertTextMessage(String liveChatId, String message)
            throws IOException, InterruptedException, ApiException {
        requireQuota(20, true);
        JsonObject details = new JsonObject();
        details.addProperty("messageText", message);
        JsonObject snippet = new JsonObject();
        snippet.addProperty("liveChatId", liveChatId);
        snippet.addProperty("type", "textMessageEvent");
        snippet.add("textMessageDetails", details);
        JsonObject body = new JsonObject();
        body.add("snippet", snippet);

        HttpRequest request = authorizedRequest(URI.create(LIVE_CHAT_MESSAGES_URI + "?part=snippet"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        validateApiResponse(response.statusCode());
    }

    private HttpRequest.Builder authorizedRequest(URI uri) throws IOException, InterruptedException, ApiException {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken())
                .header("Accept", "application/json")
                .header("User-Agent", "PatrolSpectatorPlugin");
    }

    private void validateApiResponse(int statusCode) throws ApiException {
        if (statusCode == 401) {
            accessToken = null;
            throw new ApiException(statusCode, "AUTH_ERROR");
        }
        if (statusCode < 200 || statusCode >= 300) throw new ApiException(statusCode, "API_ERROR");
    }

    private void requireQuota(int units, boolean reply) throws ApiException {
        if (!quotaBudget.tryConsume(units, reply)) throw new ApiException(0, "QUOTA_LIMIT");
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

    static LiveChatPage parseLiveChatPage(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String nextPageToken = string(root, "nextPageToken");
            long pollingIntervalMillis = root.has("pollingIntervalMillis")
                    ? Math.max(1_000L, root.get("pollingIntervalMillis").getAsLong()) : 60_000L;
            List<LiveChatMessage> messages = new ArrayList<>();
            JsonArray items = root.getAsJsonArray("items");
            if (items != null) {
                for (var element : items) {
                    JsonObject item = element.getAsJsonObject();
                    JsonObject snippet = item.getAsJsonObject("snippet");
                    JsonObject author = item.getAsJsonObject("authorDetails");
                    if (!"textMessageEvent".equals(string(snippet, "type"))) continue;
                    String id = string(item, "id");
                    String authorChannelId = string(author, "channelId");
                    String authorName = string(author, "displayName");
                    String text = string(snippet, "displayMessage");
                    if (id != null && authorChannelId != null && text != null) {
                        messages.add(new LiveChatMessage(id, authorChannelId, authorName, text));
                    }
                }
            }
            return new LiveChatPage(List.copyOf(messages), nextPageToken, pollingIntervalMillis);
        } catch (RuntimeException e) {
            return new LiveChatPage(List.of(), null, 60_000L);
        }
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

    record LiveChatPage(List<LiveChatMessage> messages, String nextPageToken, long pollingIntervalMillis) {}

    record LiveChatMessage(String id, String authorChannelId, String authorName, String text) {}
}
