package uk.co.mmuminecraftsociety.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ApiClient {
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final String baseUrl;
    private final String secret;

    ApiClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.secret = secret;
    }

    CompletableFuture<AccessDecision> access(String uuid, String username) {
        return post("/api/internal/velocity/access", new AccessRequest(uuid, username), AccessDecision.class);
    }

    CompletableFuture<SyncResponse> sync(SyncRequest request) {
        return post("/api/internal/velocity/sync", request, SyncResponse.class);
    }

    private <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(4))
                .header("authorization", "Bearer " + secret)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new ApiException("API returned HTTP " + response.statusCode());
                    }
                    try {
                        return gson.fromJson(response.body(), responseType);
                    } catch (JsonParseException exception) {
                        throw new ApiException("API returned invalid JSON", exception);
                    }
                });
    }

    record AccessRequest(String uuid, String username) { }

    record AccessDecision(String status, String code, Long expiresAtUnixMs, String websiteUrl) { }

    record SyncRequest(
            List<ServerHealth> servers,
            List<OnlinePlayer> players,
            List<Integer> acknowledgedCommandIds
    ) { }

    record ServerHealth(String name, boolean online, Long latencyMs, String error) { }

    record OnlinePlayer(String uuid, String username, String serverName) { }

    record SyncResponse(
            boolean maintenanceMode,
            List<BackendServer> servers,
            Route route,
            List<MoveCommand> commands,
            List<DisconnectPlayer> disconnects
    ) { }

    record BackendServer(int id, String name, String address, boolean isDefault) { }

    record Route(String revision, String targetServerName, Integer activeScheduleId) { }

    record MoveCommand(int id, String playerUuid, String targetServerName, long createdAtUnixMs) { }

    record DisconnectPlayer(String playerUuid, String status, Long expiresAtUnixMs) { }

    static final class ApiException extends RuntimeException {
        ApiException(String message) {
            super(message);
        }

        ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
