package dev.freddy.killshift;

import com.google.gson.JsonArray;
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
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.LivingEntity;
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
    private static final Map<UUID, Boolean> MEMBERS = new ConcurrentHashMap<>();
    private static final Map<String, KillNotice> KILL_NOTICES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "killshift-score-outbox");
        thread.setDaemon(true);
        return thread;
    });

    private record KillNotice(ServerPlayer player, Component mobName) { }

    static {
        if (BASE_URL != null && SECRET != null) {
            WORKER.scheduleWithFixedDelay(EventApi::flushScores, 0, 5, TimeUnit.SECONDS);
        }
    }

    private EventApi() { }

    static void playerJoined(ServerPlayer player) {
        CHAT_NAMES.remove(player.getUUID());
        MEMBERS.remove(player.getUUID());
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
        MEMBERS.remove(player.getUUID());
    }

    static boolean isMember(ServerPlayer player) {
        return MEMBERS.getOrDefault(player.getUUID(), false);
    }

    public static ChatType.Bound chatName(ServerPlayer player, ChatType.Bound bound) {
        Component name = CHAT_NAMES.get(player.getUUID());
        return name == null ? bound : new ChatType.Bound(bound.chatType(), name, bound.targetName());
    }

    static void completed(ServerPlayer player, LivingEntity mob) {
        Component mobName = mob.getType().getDescription();
        if (BASE_URL == null || SECRET == null) {
            player.sendSystemMessage(Component.literal("Killed ").append(mobName)
                    .append(Component.literal(". Event score unavailable.")));
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", player.getUUID().toString());
        payload.addProperty("itemId", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        payload.addProperty("occurredAtUnixMs", System.currentTimeMillis());
        payload.addProperty("notificationId", UUID.randomUUID().toString());
        String line = payload.toString();
        synchronized (OUTBOX_LOCK) {
            try {
                Files.writeString(OUTBOX, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                KILL_NOTICES.put(line, new KillNotice(player, mobName));
            } catch (Exception error) {
                System.err.println("Could not save Surprising Saturday score: " + error);
                player.sendSystemMessage(Component.literal("Killed ").append(mobName)
                        .append(Component.literal(". Event score could not be saved.")));
                return;
            }
        }
        WORKER.execute(EventApi::flushScores);
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
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status != 400 && status != 404 && (status < 200 || status >= 300)) {
                    System.err.println("Surprising Saturday score API returned " + status);
                    return;
                }
                if (status == 400 || status == 404) {
                    System.err.println("Surprising Saturday completion returned " + status + ": " + response.body());
                }
                JsonObject receipt = status >= 200 && status < 300
                        ? JsonParser.parseString(response.body()).getAsJsonObject() : null;
                KillNotice notice;
                synchronized (OUTBOX_LOCK) {
                    List<String> lines = Files.readAllLines(OUTBOX);
                    if (lines.isEmpty() || !lines.get(0).equals(line)) continue;
                    Path next = OUTBOX.resolveSibling(OUTBOX.getFileName() + ".tmp");
                    Files.write(next, lines.subList(1, lines.size()));
                    Files.move(next, OUTBOX, StandardCopyOption.REPLACE_EXISTING);
                    notice = KILL_NOTICES.remove(line);
                }
                if (notice != null) announceKill(notice, line, receipt,
                        status == 404 && response.body().contains("No list completion event accepts scores now"));
            }
        } catch (Exception error) {
            System.err.println("Surprising Saturday score replay paused: " + error);
        }
    }

    private static void announceKill(KillNotice notice, String line, JsonObject receipt, boolean noLiveEvent) {
        JsonObject officialScore = receipt != null && receipt.has("score")
                ? receipt.getAsJsonObject("score") : null;
        if (officialScore == null && !noLiveEvent) {
            try {
                JsonObject report = JsonParser.parseString(line).getAsJsonObject();
                String uuid = report.get("playerUuid").getAsString();
                long at = report.get("occurredAtUnixMs").getAsLong();
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL
                                + "/api/internal/surprising-saturday/score/" + uuid + "?atUnixMs=" + at))
                        .timeout(Duration.ofSeconds(5))
                        .header("authorization", "Bearer " + SECRET)
                        .GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    officialScore = JsonParser.parseString(response.body()).getAsJsonObject();
                } else {
                    System.err.println("Surprising Saturday score lookup returned " + response.statusCode()
                            + ": " + response.body());
                }
            } catch (Exception error) {
                System.err.println("Surprising Saturday score lookup failed: " + error);
            }
        }

        JsonObject score = officialScore;
        Component podiumResult;
        try {
            podiumResult = podiumMessage(receipt);
        } catch (Exception error) {
            System.err.println("Surprising Saturday podium announcement failed: " + error);
            podiumResult = null;
        }
        Component podium = podiumResult;
        var server = notice.player().level().getServer();
        server.execute(() -> {
            ServerPlayer player = notice.player();
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                player.sendSystemMessage(killMessage(notice.mobName(), score, noLiveEvent));
            }
            if (podium != null) {
                for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
                    recipient.sendSystemMessage(podium);
                }
            }
        });
    }

    private static Component killMessage(Component mobName, JsonObject score, boolean noLiveEvent) {
        var message = Component.literal("You killed ").append(mobName);
        if (score == null) {
            return message.append(Component.literal(noLiveEvent
                    ? ". No live scoring event was active for this kill."
                    : ". Event score unavailable."));
        }
        StringJoiner animals = new StringJoiner(", ");
        for (var item : score.getAsJsonArray("completed")) {
            animals.add(item.getAsJsonObject().get("name").getAsString());
        }
        return message.append(Component.literal(". Score: " + score.get("score").getAsInt()
                + ". Scored mobs: " + (animals.length() == 0 ? "none" : animals.toString()) + "."));
    }

    private static Component podiumMessage(JsonObject receipt) {
        if (receipt == null || !receipt.has("podiumChange") || receipt.get("podiumChange").isJsonNull()) {
            return null;
        }
        JsonObject change = receipt.getAsJsonObject("podiumChange");
        JsonArray before = change.getAsJsonArray("before");
        JsonArray after = change.getAsJsonArray("after");
        String uuid = change.get("playerUuid").getAsString();
        int oldRank = rankOf(before, uuid);
        int newRank = rankOf(after, uuid);
        MutableComponent message = Component.empty();
        if (newRank == 0) {
            message.append(Component.literal("The podium changes!"));
        } else {
            message.append(coloredName(after.get(newRank - 1).getAsJsonObject()));
            if (before.isEmpty()) {
                message.append(Component.literal(" opens the leaderboard!"));
            } else if (newRank == 1) {
                message.append(Component.literal(" takes ")).append(rankPlace(1))
                        .append(Component.literal(" from "))
                        .append(coloredName(before.get(0).getAsJsonObject()))
                        .append(Component.literal("!"));
            } else if (oldRank == 0) {
                message.append(Component.literal(" breaks into ")).append(rankPlace(newRank));
                if (newRank <= before.size()) {
                    message.append(Component.literal(" ahead of "))
                            .append(coloredName(before.get(newRank - 1).getAsJsonObject()));
                }
                message.append(Component.literal("!"));
            } else if (newRank < oldRank) {
                message.append(Component.literal(" climbs from ")).append(rankPlace(oldRank))
                        .append(Component.literal(" to ")).append(rankPlace(newRank))
                        .append(Component.literal(" ahead of "))
                        .append(coloredName(before.get(newRank - 1).getAsJsonObject()))
                        .append(Component.literal("!"));
            } else {
                message.append(Component.literal(" changes the podium!"));
            }
        }
        message.append(Component.literal(" Podium: "));
        for (int i = 0; i < 3; i++) {
            if (i > 0) message.append(Component.literal(", "));
            message.append(rankPlace(i + 1)).append(Component.literal(": "));
            if (i < after.size()) {
                JsonObject entry = after.get(i).getAsJsonObject();
                message.append(coloredName(entry))
                        .append(Component.literal(" (" + entry.get("score").getAsInt() + ")"));
            } else {
                message.append(Component.literal("open"));
            }
        }
        return message.append(Component.literal("."));
    }

    private static int rankOf(JsonArray podium, String uuid) {
        for (int i = 0; i < podium.size(); i++) {
            if (podium.get(i).getAsJsonObject().get("uuid").getAsString().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String ordinal(int rank) {
        return rank == 1 ? "1st" : rank == 2 ? "2nd" : "3rd";
    }

    private static Component rankPlace(int rank) {
        int color = rank == 1 ? 0xFFD700 : rank == 2 ? 0xC0C0C0 : 0xCD7F32;
        return Component.literal(ordinal(rank) + " place").withColor(color);
    }

    private static Component coloredName(JsonObject player) {
        return coloredName(player.get("name").getAsString(), player.get("color").getAsString(),
                player.get("role").getAsString());
    }

    private static Component coloredName(String name, String colorHex, String role) {
        int color = Integer.parseInt(colorHex.substring(1), 16);
        MutableComponent result = Component.literal(name).withColor(color);
        if (!role.equals("Player")) {
            ChatFormatting labelColor = role.equals("Committee") ? ChatFormatting.AQUA
                    : role.equals("External") ? ChatFormatting.GRAY : ChatFormatting.GREEN;
            result.append(Component.literal(" [" + role + "]").withStyle(labelColor));
        }
        return result;
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
        if (team.canSeeFriendlyInvisibles()) team.setSeeFriendlyInvisibles(false);
        String nickname = profile.get("nickname").getAsString();
        String pronouns = profile.get("pronouns").getAsString();
        String role = profile.get("role").getAsString();
        MEMBERS.put(player.getUUID(), profile.has("isMember")
                ? profile.get("isMember").getAsBoolean()
                : role.equals("Member") || role.equals("Committee"));
        int color = Integer.parseInt(profile.get("color").getAsString().substring(1), 16);
        CHAT_NAMES.put(player.getUUID(), coloredName(player.getScoreboardName(),
                profile.get("color").getAsString(), role));
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
            if (op == null || op.permissions().level() != PermissionLevel.GAMEMASTERS) {
                playerList.op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.of(false));
            }
        } else if (op != null) {
            playerList.deop(player.nameAndId());
        }
    }
}
