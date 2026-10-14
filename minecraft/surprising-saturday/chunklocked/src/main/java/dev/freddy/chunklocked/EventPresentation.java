package dev.freddy.chunklocked;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class EventPresentation {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Map<UUID, Component> CHAT_NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COLORS = new ConcurrentHashMap<>();
    private static final Map<UUID, DiscordPresentation> DISCORD_PRESENTATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Profile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> RENDERED_PROFILES = new ConcurrentHashMap<>();

    record DiscordPresentation(String role, String colorHex) { }
    private record Profile(String nickname, String pronouns, boolean showDeathCounter) { }

    private EventPresentation() { }

    static void playerJoined(ServerPlayer player) {
        CHAT_NAMES.remove(player.getUUID());
        COLORS.remove(player.getUUID());
        DISCORD_PRESENTATIONS.remove(player.getUUID());
        PROFILES.remove(player.getUUID());
        RENDERED_PROFILES.remove(player.getUUID());
        PlayerTeam team = player.getTeam();
        if (team != null && team.getName().startsWith("ss")) {
            team.setPlayerPrefix(Component.empty());
            team.setPlayerSuffix(Component.empty());
        }
        applyColor(player, colorFor(player));
        refresh(player, () -> EventDiscordBridge.publishJoin(player));
    }

    static void playerLeft(ServerPlayer player) {
        CHAT_NAMES.remove(player.getUUID());
        COLORS.remove(player.getUUID());
        DISCORD_PRESENTATIONS.remove(player.getUUID());
        PROFILES.remove(player.getUUID());
        RENDERED_PROFILES.remove(player.getUUID());
    }

    static void playerRespawned(ServerPlayer player) {
        applyColor(player, colorFor(player));
        RENDERED_PROFILES.remove(player.getUUID());
        updateBelowName(player);
        refresh(player);
    }

    static void refresh(ServerPlayer player) {
        refresh(player, () -> { });
    }

    private static void refresh(ServerPlayer player, Runnable afterRefresh) {
        if (BASE_URL == null || SECRET == null) {
            afterRefresh.run();
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL
                        + "/api/internal/surprising-saturday/player-data/" + player.getUUID()))
                .timeout(Duration.ofSeconds(5))
                .header("authorization", "Bearer " + SECRET)
                .GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
                player.level().getServer().execute(() -> {
                    if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player
                            || player.hasDisconnected()) return;
                    if (error != null) {
                        System.err.println("Chunklocked player data failed: " + error);
                    } else if (response.statusCode() != 200) {
                        System.err.println("Chunklocked player data API returned " + response.statusCode());
                    } else {
                        try {
                            apply(player, JsonParser.parseString(response.body()).getAsJsonObject());
                        } catch (Exception parseError) {
                            System.err.println("Chunklocked player data was invalid: " + parseError);
                        }
                    }
                    afterRefresh.run();
                }));
    }

    private static void apply(ServerPlayer player, JsonObject profile) {
        int color = PlayerColors.parse(profile.get("color").getAsString());
        String role = profile.get("role").getAsString();
        DISCORD_PRESENTATIONS.put(player.getUUID(),
                new DiscordPresentation(role, profile.get("color").getAsString()));
        CHAT_NAMES.put(player.getUUID(), coloredName(player.getScoreboardName(), color, role));
        PROFILES.put(player.getUUID(), new Profile(profile.get("nickname").getAsString(),
                profile.get("pronouns").getAsString(), profile.get("showDeathCounter").getAsBoolean()));
        applyColor(player, color);

        PlayerTeam team = playerTeam(player);
        String label = role.equals("Player") ? "" : " [" + role + "]";
        ChatFormatting labelColor = role.equals("Committee") ? ChatFormatting.AQUA
                : role.equals("External") ? ChatFormatting.GRAY : ChatFormatting.GREEN;
        team.setPlayerPrefix(Component.empty());
        team.setPlayerSuffix(Component.literal(label).withStyle(labelColor));

        updateBelowName(player);
        player.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    static void updateBelowName(ServerPlayer player) {
        Profile profile = PROFILES.get(player.getUUID());
        if (profile == null) return;
        String name = profile.nickname().isBlank() ? profile.pronouns()
                : profile.pronouns().isBlank() ? profile.nickname()
                : profile.nickname() + " - " + profile.pronouns();
        long dangerCount = (long) player.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING))
                + player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        String danger = profile.showDeathCounter() ? dangerCount + "☠" : "";
        String text = name + (name.isEmpty() || danger.isEmpty() ? "" : " - ") + danger;
        if (text.equals(RENDERED_PROFILES.put(player.getUUID(), text))) return;
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective("chunklocked_profile");
        if (objective == null) {
            objective = scoreboard.addObjective("chunklocked_profile", ObjectiveCriteria.DUMMY,
                    Component.empty(), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        if (scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME) != objective) {
            scoreboard.setDisplayObjective(DisplaySlot.BELOW_NAME, objective);
        }
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, objective);
        score.set(0);
        score.numberFormatOverride(new FixedFormat(Component.literal(text)));
    }

    private static void applyColor(ServerPlayer player, int color) {
        COLORS.put(player.getUUID(), color);
        var waypoints = player.level().getWaypointManager();
        waypoints.untrackWaypoint(player);
        player.waypointIcon().color = Optional.of(color);
        waypoints.trackWaypoint(player);
        PlayerTeam team = playerTeam(player);
        team.setDisplayName(Component.literal(player.getScoreboardName()).withColor(color));
        team.setColor(Optional.of(PlayerColors.closestTeamColor(color)));
        player.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    private static PlayerTeam playerTeam(ServerPlayer player) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        String teamName = "ss" + player.getUUID().toString().replace("-", "").substring(0, 14);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);
        if (player.getTeam() != team) scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        if (team.canSeeFriendlyInvisibles()) team.setSeeFriendlyInvisibles(false);
        return team;
    }

    public static int colorFor(Player player) {
        Integer color = COLORS.get(player.getUUID());
        if (color != null) return color;
        PlayerTeam team = player.getTeam();
        if (team != null && team.getName().startsWith("ss")) {
            var savedColor = team.getDisplayName().getStyle().getColor();
            if (savedColor != null) return savedColor.getValue();
        }
        return PlayerColors.defaultColor(player.getUUID());
    }

    static Component coloredName(ServerPlayer player) {
        return coloredName(player.getScoreboardName(), colorFor(player), discordPresentation(player).role());
    }

    private static Component coloredName(String name, int color, String role) {
        MutableComponent result = Component.literal(name).withColor(color);
        if (!role.equals("Player")) {
            ChatFormatting labelColor = role.equals("Committee") ? ChatFormatting.AQUA
                    : role.equals("External") ? ChatFormatting.GRAY : ChatFormatting.GREEN;
            result.append(Component.literal(" [" + role + "]").withStyle(labelColor));
        }
        return result;
    }

    public static ChatType.Bound chatName(ServerPlayer player, ChatType.Bound bound) {
        Component name = CHAT_NAMES.get(player.getUUID());
        return name == null ? bound : new ChatType.Bound(bound.chatType(), name, bound.targetName());
    }

    static DiscordPresentation discordPresentation(ServerPlayer player) {
        return DISCORD_PRESENTATIONS.getOrDefault(player.getUUID(),
                new DiscordPresentation("Player", String.format(Locale.ROOT, "#%06X", colorFor(player))));
    }
}
