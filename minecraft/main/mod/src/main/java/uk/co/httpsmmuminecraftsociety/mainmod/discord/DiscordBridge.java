package uk.co.httpsmmuminecraftsociety.mainmod.discord;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PublishDiscordEventRequest;

import java.util.stream.Collectors;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class DiscordBridge {
    private static boolean broadcastingDiscordMessage;
	private static boolean broadcastingFishAnnouncement;
    private static final Set<PlayerChatMessage> commandMessages = Collections.newSetFromMap(new IdentityHashMap<>());

    private DiscordBridge() { }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) -> {
            if (!commandMessages.remove(message)) publish("chat", sender, message.signedContent());
        });
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
			if (!broadcastingDiscordMessage && !broadcastingFishAnnouncement && !overlay && !isCoveredPlayerEvent(message)) {
                publish("server", null, message.getString());
            }
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, type) -> {
            commandMessages.add(message);
            publish("server", null, message.decoratedContent().getString());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            boolean firstJoin = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 1;
            PlayerStatsSync.syncNow(player).thenRun(() -> server.execute(() -> {
                if (!player.hasDisconnected()) publish(
                        firstJoin ? "first_join" : "join",
                        player,
                        firstJoin ? "joined the server for the first time. (Players online: " + onlinePlayers(server, null) + ")"
                                : "joined the server. (Players online: " + onlinePlayers(server, null) + ")"
                );
            }));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> publish(
                "leave",
                handler.player,
                "left the server. (Players online: " + onlinePlayers(server, handler.player) + ")"
        ));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            String message = player.getCombatTracker().getDeathMessage().getString();
            String displayName = player.getDisplayName().getString();
            publish("death", player, message.startsWith(displayName)
                    ? message.substring(displayName.length()).stripLeading()
                    : message);
        });
    }

    public static void advancement(ServerPlayer player, String title, int dabloons) {
        String content = "has made the advancement [" + title + "] and earned " + dabloons + " Dabloons.";
        Component message = Component.empty().append(player.getDisplayName()).append(" " + content);
        player.level().getServer().getPlayerList().getPlayers().stream()
                .filter(recipient -> recipient != player && !recipient.hasDisconnected())
                .forEach(recipient -> recipient.sendSystemMessage(message));
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
		broadcastingFishAnnouncement = true;
		try {
			server.getPlayerList().broadcastSystemMessage(Component.literal((firstServerCatch ? "🐟 👶 " : "🐟 ")
					+ player.getName().getString() + " " + content), false);
		} finally {
			broadcastingFishAnnouncement = false;
		}
		playerEvent(firstServerCatch ? "fish_first" : "fish", player, content);
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
                .map(DiscordBridge::coloredOnlinePlayer)
                .collect(Collectors.joining(", "));
        return players.isEmpty() ? "none" : players;
    }

    private static String coloredOnlinePlayer(ServerPlayer player) {
        PlayerStatsSync.DiscordPresentation profile = PlayerStatsSync.discordPresentation(player);
        String label = switch (profile.role()) {
            case "Committee" -> "\u001B[36m [Committee]";
            case "Member" -> "\u001B[32m [Member]";
            case "External" -> "\u001B[30m [External]";
            default -> "";
        };
        return ansiColor(profile.colorHex()) + player.getName().getString() + "\u001B[0m" + label + "\u001B[0m";
    }

    private static String ansiColor(String color) {
        int rgb;
        try {
            rgb = color.startsWith("#") ? Integer.parseInt(color.substring(1), 16) : 0xE6E6E6;
        } catch (NumberFormatException ignored) {
            rgb = 0xE6E6E6;
        }
        int[][] choices = {{31, 255, 0, 0}, {33, 255, 255, 0}, {32, 0, 200, 0}, {34, 0, 100, 255},
                {35, 160, 32, 240}, {30, 128, 128, 128}, {37, 255, 255, 255}};
        int closest = 37;
        int shortestDistance = Integer.MAX_VALUE;
        for (int[] choice : choices) {
            int red = (rgb >> 16 & 0xFF) - choice[1];
            int green = (rgb >> 8 & 0xFF) - choice[2];
            int blue = (rgb & 0xFF) - choice[3];
            int distance = red * red + green * green + blue * blue;
            if (distance < shortestDistance) {
                closest = choice[0];
                shortestDistance = distance;
            }
        }
        return "\u001B[" + closest + "m";
    }

    private static boolean isCoveredPlayerEvent(Component message) {
        if (!(message.getContents() instanceof TranslatableContents translated)) return false;
        String key = translated.getKey();
        return key.startsWith("multiplayer.player.joined")
                || key.startsWith("multiplayer.player.left")
                || key.startsWith("chat.type.advancement")
                || key.startsWith("death.");
    }
}
