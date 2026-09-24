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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class EventApi {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Path OUTBOX = Path.of("/data/killshift-score-outbox.jsonl");
    private static final Object OUTBOX_LOCK = new Object();
    private static final Map<UUID, Component> CHAT_NAMES = new ConcurrentHashMap<>();

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

    static void playerJoined(ServerPlayer player) {
        CHAT_NAMES.remove(player.getUUID());
        PlayerTeam team = player.getTeam();
        if (team != null && team.getName().startsWith("ss")) {
            team.setPlayerPrefix(Component.empty());
            team.setPlayerSuffix(Component.empty());
        }
        var playerList = player.level().getServer().getPlayerList();
        if (playerList.isOp(player.nameAndId())) playerList.deop(player.nameAndId());
        refreshPresentation(player);
    }

    static void playerLeft(ServerPlayer player) {
        CHAT_NAMES.remove(player.getUUID());
    }

    public static ChatType.Bound chatName(ServerPlayer player, ChatType.Bound bound) {
        Component name = CHAT_NAMES.get(player.getUUID());
        return name == null ? bound : new ChatType.Bound(bound.chatType(), name, bound.targetName());
    }

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
            player.level().getServer().execute(() -> {
                if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                    applyPresentation(player, profile);
                }
            });
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
        ChatFormatting labelColor = role.equals("Committee") ? ChatFormatting.AQUA
                : role.equals("External") ? ChatFormatting.GRAY : ChatFormatting.GREEN;
        Component chatName = Component.literal(player.getScoreboardName()).withColor(color);
        if (!role.equals("Player")) {
            chatName = chatName.copy().append(Component.literal(" [" + role + "]").withStyle(labelColor));
        }
        CHAT_NAMES.put(player.getUUID(), chatName);
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
        team.setColor(Optional.of(nearest));
        team.setPlayerPrefix(Component.empty());
        team.setPlayerSuffix(Component.empty());

        String nameTag = nickname.isBlank() ? pronouns
                : pronouns.isBlank() ? nickname : nickname + " - " + pronouns;
        Objective objective = scoreboard.getObjective("killshift_profile");
        if (objective == null) {
            objective = scoreboard.addObjective("killshift_profile", ObjectiveCriteria.DUMMY, Component.empty(),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        if (scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME) != objective) {
            scoreboard.setDisplayObjective(DisplaySlot.BELOW_NAME, objective);
        }
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, objective);
        score.set(0);
        score.numberFormatOverride(new FixedFormat(Component.literal(nameTag).withColor(color)));

        var playerList = player.level().getServer().getPlayerList();
        var op = playerList.getOps().get(player.nameAndId());
        if (role.equals("Committee")) {
            if (op == null || op.permissions().level() != PermissionLevel.MODERATORS) {
                playerList.op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.MODERATOR), Optional.of(false));
            }
        } else if (op != null) {
            playerList.deop(player.nameAndId());
        }
    }
}
