package uk.co.httpsmmuminecraftsociety.mainmod.discord;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import uk.co.httpsmmuminecraftsociety.mainmod.BedrockFormatting;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PublishDiscordEventRequest;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class DiscordBridge {
    private static boolean broadcastingDiscordMessage;
    private static boolean broadcastingPlayerAnnouncement;
    private static final Set<PlayerChatMessage> commandMessages = Collections.newSetFromMap(new IdentityHashMap<>());

    private DiscordBridge() { }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) -> {
            if (!commandMessages.remove(message)) publish("chat", sender, message.signedContent());
        });
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (broadcastingDiscordMessage || broadcastingPlayerAnnouncement || overlay) return;
            ServerPlayer deadPlayer = deathPlayer(server, message);
            if (deadPlayer != null) {
                String deathMessage = message.getString();
                String displayName = deadPlayer.getDisplayName().getString();
                publish("death", deadPlayer, deathMessage.startsWith(displayName)
                        ? deathMessage.substring(displayName.length()).stripLeading()
                        : deathMessage);
            } else if (!isCoveredPlayerEvent(message)) {
                publish("server", null, message.getString());
            }
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, type) -> {
            commandMessages.add(message);
            String content = message.decoratedContent().getString();
            if (!content.startsWith("Server update starting now.")) publish("server", null, content);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            boolean firstJoin = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 1;
            PlayerStatsSync.syncNow(player).thenRun(() -> server.execute(() -> {
                if (!player.hasDisconnected()) {
                    publish(
                            firstJoin ? "first_join" : "join",
                            player,
                            (firstJoin ? "joined the server for the first time." : "joined the server.")
                                    + " (" + onlinePlayerCount(server, null) + " online)"
                    );
                }
            }));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            publish(
                    "leave",
                    handler.player,
                    "left the server. (" + onlinePlayerCount(server, handler.player) + " online)"
            );
        });
    }

    public static void advancement(ServerPlayer player, String action, String title, int dabloons) {
        String content = action + " [" + title + "] and earned " + dabloons + " Dabloons.";
        publish("advancement", player, content);
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

    public static void fishAnnouncement(MinecraftServer server, ServerPlayer player, String content, boolean firstServerCatch) {
        playerAnnouncement(server, firstServerCatch ? "fish_first" : "fish", "🐟", player, content);
    }

    public static void shopAnnouncement(MinecraftServer server, ServerPlayer player, String content) {
        playerAnnouncement(server, "shop", "🛒", player, content);
    }

    private static void playerAnnouncement(
            MinecraftServer server,
            String type,
            String icon,
            ServerPlayer player,
            String content
    ) {
        broadcastingPlayerAnnouncement = true;
        try {
            server.getPlayerList().broadcastSystemMessage(Component.empty()
                    .append(Component.literal(icon + " "))
                    .append(player.getDisplayName())
                    .append(Component.literal(" " + content)), false);
        } finally {
            broadcastingPlayerAnnouncement = false;
        }
        playerEvent(type, player, content);
    }

    private static void publish(String type, ServerPlayer player, String content) {
        PlayerStatsSync.DiscordPresentation profile = player == null
                ? new PlayerStatsSync.DiscordPresentation("", "", "", "")
                : PlayerStatsSync.discordPresentation(player);
        PublishDiscordEventRequest.Builder request = PublishDiscordEventRequest.newBuilder()
                .setType(type)
                .setContent(BedrockFormatting.toDiscord(content))
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

    private static long onlinePlayerCount(MinecraftServer server, ServerPlayer excluded) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> player != excluded && !player.hasDisconnected())
                .count();
    }

    private static boolean isCoveredPlayerEvent(Component message) {
        if (!(message.getContents() instanceof TranslatableContents translated)) return false;
        String key = translated.getKey();
        return key.startsWith("multiplayer.player.joined")
                || key.startsWith("multiplayer.player.left")
                || key.startsWith("chat.type.advancement")
                || key.startsWith("death.");
    }

    private static ServerPlayer deathPlayer(MinecraftServer server, Component message) {
        if (!(message.getContents() instanceof TranslatableContents translated)
                || !translated.getKey().startsWith("death.")) return null;
        String displayName = translated.getArgument(0).getString();
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> player.getDisplayName().getString().equals(displayName))
                .findFirst()
                .orElse(null);
    }
}
