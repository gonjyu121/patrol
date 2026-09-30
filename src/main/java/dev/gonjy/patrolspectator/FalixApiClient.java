package dev.gonjy.patrolspectator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class FalixApiClient {
    private static final String BASE_URL = "https://client.falixnodes.net/api/v2/servers/";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private final HttpClient httpClient;

    FalixApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    FalixApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    CompletableFuture<FalixDiagnosticsResult> inspect(FalixApiSettings settings) {
        if (!settings.configured()) return CompletableFuture.completedFuture(FalixDiagnosticsResult.notConfigured());
        List<Allocation> allocations = new ArrayList<>();
        return fetchPage(settings, 0, 0, allocations)
                .thenApply(ignored -> summarize(allocations, settings.geyserAllocationNote()))
                .exceptionally(FalixApiClient::failureResult);
    }

    private CompletableFuture<Void> fetchPage(FalixApiSettings settings, int offset, int page,
                                                List<Allocation> allocations) {
        if (page >= MAX_PAGES) return CompletableFuture.failedFuture(new IllegalStateException("Too many pages"));
        URI uri = URI.create(BASE_URL + settings.serverId() + "/allocations?limit=" + PAGE_SIZE + "&offset=" + offset);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        throw new FalixHttpException(response.statusCode());
                    }
                    Page parsed = parsePage(response.body());
                    allocations.addAll(parsed.allocations());
                    if (!parsed.hasMore()) return CompletableFuture.completedFuture(null);
                    return fetchPage(settings, offset + PAGE_SIZE, page + 1, allocations);
                });
    }

    private static Page parsePage(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null) throw new IllegalArgumentException("Missing data");
        List<Allocation> allocations = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject object = element.getAsJsonObject();
            allocations.add(new Allocation(
                    requiredInt(object, "port"),
                    requiredBoolean(object, "is_primary"),
                    nullableString(object, "notes"),
                    firstNonBlank(nullableString(object, "display_address"), nullableString(object, "ip_alias"))));
        }
        JsonObject pagination = root.getAsJsonObject("pagination");
        boolean hasMore = pagination != null && pagination.has("has_more")
                && pagination.get("has_more").getAsBoolean();
        return new Page(allocations, hasMore);
    }

    static FalixDiagnosticsResult summarize(List<Allocation> allocations, String geyserNote) {
        List<Allocation> primaries = allocations.stream().filter(Allocation::primary).toList();
        List<Allocation> geyserCandidates = allocations.stream()
                .filter(allocation -> !allocation.primary())
                .filter(allocation -> allocation.notes() != null
                        && allocation.notes().trim().equalsIgnoreCase(geyserNote.trim()))
                .toList();
        Allocation primary = primaries.size() == 1 ? primaries.getFirst() : null;
        Allocation geyser = geyserCandidates.size() == 1 ? geyserCandidates.getFirst() : null;
        return new FalixDiagnosticsResult(
                "OK",
                primary == null ? null : primary.address(),
                primary == null ? null : primary.port(),
                geyser == null ? null : geyser.port());
    }

    private static FalixDiagnosticsResult failureResult(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof FalixHttpException http) {
            return FalixDiagnosticsResult.unavailable(switch (http.statusCode) {
                case 401 -> "AUTH ERROR";
                case 403 -> "FORBIDDEN";
                case 404 -> "SERVER NOT FOUND";
                case 429 -> "RATE LIMITED";
                default -> http.statusCode >= 500 ? "UNAVAILABLE" : "INVALID RESPONSE";
            });
        }
        if (cause instanceof IllegalArgumentException) {
            return FalixDiagnosticsResult.unavailable("INVALID RESPONSE");
        }
        return FalixDiagnosticsResult.unavailable("UNAVAILABLE");
    }

    private static int requiredInt(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("Missing " + key);
        return object.get(key).getAsInt();
    }

    private static boolean requiredBoolean(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("Missing " + key);
        return object.get(key).getAsBoolean();
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    record Allocation(int port, boolean primary, String notes, String address) {}
    private record Page(List<Allocation> allocations, boolean hasMore) {}
    private static final class FalixHttpException extends RuntimeException {
        private final int statusCode;
        FalixHttpException(int statusCode) {
            super("Falix API request failed");
            this.statusCode = statusCode;
        }
    }
}
