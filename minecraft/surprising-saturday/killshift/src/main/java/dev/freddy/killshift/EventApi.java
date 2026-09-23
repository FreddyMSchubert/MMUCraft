package dev.freddy.killshift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

final class EventApi {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Path OUTBOX = Path.of("/data/killshift-score-outbox.jsonl");
    private static final Object OUTBOX_LOCK = new Object();

    static {
        if (BASE_URL != null && SECRET != null) {
            var worker = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "killshift-score-outbox");
                thread.setDaemon(true);
                return thread;
            });
            worker.scheduleWithFixedDelay(EventApi::flushScores, 0, 5, TimeUnit.SECONDS);
        }
    }

    private EventApi() { }

    static void completed(ServerPlayer player, Mob mob) {
        if (BASE_URL == null || SECRET == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", player.getUUID().toString());
        payload.addProperty("itemId", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        payload.addProperty("completed", true);
        payload.addProperty("occurredAtUnixMs", System.currentTimeMillis());
        synchronized (OUTBOX_LOCK) {
            try {
                Files.writeString(OUTBOX, payload + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception error) {
                System.err.println("Could not save Surprising Saturday score: " + error);
            }
        }
    }

    private static void flushScores() {
        try {
            while (true) {
                String line;
                synchronized (OUTBOX_LOCK) {
                    if (!Files.exists(OUTBOX)) return;
                    List<String> lines = Files.readAllLines(OUTBOX);
                    if (lines.isEmpty()) return;
                    line = lines.get(0);
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/internal/surprising-saturday/list-completion"))
                        .timeout(Duration.ofSeconds(5))
                        .header("authorization", "Bearer " + SECRET)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(line))
                        .build();
                int status = HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status != 400 && status != 404 && (status < 200 || status >= 300)) {
                    System.err.println("Surprising Saturday score API returned " + status);
                    return;
                }
                synchronized (OUTBOX_LOCK) {
                    List<String> lines = Files.readAllLines(OUTBOX);
                    if (lines.isEmpty() || !lines.get(0).equals(line)) continue;
                    Path next = OUTBOX.resolveSibling(OUTBOX.getFileName() + ".tmp");
                    Files.write(next, lines.subList(1, lines.size()));
                    Files.move(next, OUTBOX, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception error) {
            System.err.println("Surprising Saturday score replay paused: " + error);
        }
    }

    static void refreshPresentation(ServerPlayer player) {
        if (BASE_URL == null || SECRET == null) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/internal/surprising-saturday/player-data/" + player.getUUID()))
                .timeout(Duration.ofSeconds(5))
                .header("authorization", "Bearer " + SECRET)
                .GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) {
                System.err.println("Surprising Saturday player data API returned " + response.statusCode());
                return;
            }
            JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
            player.level().getServer().execute(() -> applyPresentation(player, profile));
        }).exceptionally(error -> { System.err.println("Surprising Saturday player data failed: " + error); return null; });
    }

    private static void applyPresentation(ServerPlayer player, JsonObject profile) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        String teamName = "ss" + player.getUUID().toString().replace("-", "").substring(0, 14);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        String nickname = profile.get("nickname").getAsString();
        String pronouns = profile.get("pronouns").getAsString();
        String role = profile.get("role").getAsString();
        int color = Integer.parseInt(profile.get("color").getAsString().substring(1), 16);
        TeamColor nearest = TeamColor.WHITE;
        int nearestDistance = Integer.MAX_VALUE;
        for (TeamColor candidate : TeamColor.VALUES) {
            int other = candidate.rgb();
            int red = ((color >> 16) & 255) - ((other >> 16) & 255);
            int green = ((color >> 8) & 255) - ((other >> 8) & 255);
            int blue = (color & 255) - (other & 255);
            int distance = red * red + green * green + blue * blue;
            if (distance < nearestDistance) { nearest = candidate; nearestDistance = distance; }
        }
        team.setColor(java.util.Optional.of(nearest));
        team.setPlayerPrefix(Component.literal((nickname.equals(player.getScoreboardName()) ? "" : nickname + " · ") + (pronouns.isBlank() ? "" : pronouns + " · ")).withColor(color));
        team.setPlayerSuffix(Component.literal(" [" + role + "]").withColor(color));
    }
}
