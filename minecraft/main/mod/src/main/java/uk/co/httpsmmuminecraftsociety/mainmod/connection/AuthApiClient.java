package uk.co.httpsmmuminecraftsociety.mainmod.connection;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class AuthApiClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private final String baseUrl = System.getenv().getOrDefault("AUTH_API_BASE_URL", "http://api:8080");

    public void checkAuthentication(UUID playerUuid, BiConsumer<AuthStatusResponse, Throwable> callback) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/minecraft/players/" + playerUuid + "/status"))
                .timeout(REQUEST_TIMEOUT)
                .header("accept", "application/json")
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Auth status check failed with HTTP " + response.statusCode());
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    boolean authenticated = json.get("authenticated").getAsBoolean();
                    return new AuthStatusResponse(authenticated);
                })
                .whenComplete((result, error) -> callback.accept(result, error));
    }

    public void startRegistration(UUID playerUuid, String username, BiConsumer<RegistrationResponse, Throwable> callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", playerUuid.toString());
        payload.addProperty("username", username);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/minecraft/registration-sessions"))
                .timeout(REQUEST_TIMEOUT)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Registration start failed with HTTP " + response.statusCode());
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    boolean authenticated = json.get("authenticated").getAsBoolean();
                    String loginUrl = json.has("loginUrl") && !json.get("loginUrl").isJsonNull()
                            ? json.get("loginUrl").getAsString()
                            : null;

                    return new RegistrationResponse(authenticated, loginUrl);
                })
                .whenComplete((result, error) -> callback.accept(result, error));
    }

    public record AuthStatusResponse(boolean authenticated) {
    }

    public record RegistrationResponse(boolean authenticated, String loginUrl) {
    }
}
