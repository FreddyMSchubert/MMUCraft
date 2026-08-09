package uk.co.httpsmmuminecraftsociety.mainmod.discord;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PublishDiscordEventRequest;

import java.util.stream.Collectors;

public final class DiscordBridge {
    private static boolean broadcastingDiscordMessage;

    private DiscordBridge() { }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) ->
                publish("chat", sender, message.signedContent()));
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (!broadcastingDiscordMessage && !overlay && !isCoveredPlayerEvent(message)) {
                publish("server", null, message.getString());
            }
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, type) -> {
            if (!source.isPlayer()) publish("server", null, message.decoratedContent().getString());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            boolean firstJoin = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 1;
            PlayerStatsSync.syncNow(player).thenRun(() -> server.execute(() -> {
                if (!player.hasDisconnected()) publish(
                        firstJoin ? "first_join" : "join",
                        player,
                        firstJoin ? "I just joined the server for the first time! Players online: " + onlinePlayers(server, null)
                                : "I just joined the server. Players online: " + onlinePlayers(server, null)
                );
            }));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> publish(
                "leave",
                handler.player,
                "I just left the server. Players online: " + onlinePlayers(server, handler.player)
        ));
    }

    public static void advancement(ServerPlayer player, String title) {
        publish("advancement", player, "I just completed " + title + ".");
    }

    public static void playerEvent(String type, ServerPlayer player, String content) {
        publish(type, player, content);
    }

    public static void broadcastFromDiscord(MinecraftServer server, Component message) {
        broadcastingDiscordMessage = true;
        try {
            server.getPlayerList().broadcastSystemMessage(message, false);
        } finally {
            broadcastingDiscordMessage = false;
        }
    }

    private static void publish(String type, ServerPlayer player, String content) {
        PlayerStatsSync.DiscordPresentation profile = player == null
                ? new PlayerStatsSync.DiscordPresentation("", "", "", "")
                : PlayerStatsSync.discordPresentation(player);
        PublishDiscordEventRequest.Builder request = PublishDiscordEventRequest.newBuilder()
                .setType(type)
                .setContent(content)
                .setRole(profile.role())
                .setNickname(profile.nickname())
                .setPronouns(profile.pronouns())
                .setColorHex(profile.colorHex());
        if (player != null) request
                .setMinecraftUsername(player.getName().getString())
                .setMinecraftUuid(player.getUUID().toString());
        GameplayGrpcService.publishDiscordEvent(request.build()).exceptionally(error -> {
            MainMod.LOGGER.debug("Failed to publish {} event to Discord", type, error);
            return null;
        });
    }

    private static String onlinePlayers(MinecraftServer server, ServerPlayer excluded) {
        String players = server.getPlayerList().getPlayers().stream()
                .filter(player -> player != excluded && !player.hasDisconnected())
                .map(player -> player.getName().getString())
                .collect(Collectors.joining(", "));
        return players.isEmpty() ? "none" : players;
    }

    private static boolean isCoveredPlayerEvent(Component message) {
        if (!(message.getContents() instanceof TranslatableContents translated)) return false;
        String key = translated.getKey();
        return key.startsWith("multiplayer.player.joined")
                || key.startsWith("multiplayer.player.left")
                || key.startsWith("chat.type.advancement");
    }
}
