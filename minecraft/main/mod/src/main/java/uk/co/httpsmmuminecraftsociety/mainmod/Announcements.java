package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.Announcement;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;

import java.net.URI;
import java.util.List;

public final class Announcements {
    private Announcements() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("announcements")
                        .requires(source -> source.getPlayer() != null)
                        .executes(context -> {
                            request(context.getSource().getPlayerOrException(), true);
                            return 1;
                        })
        ));
    }

    public static void sendUnread(ServerPlayer player) {
        request(player, false);
    }

    public static void send(ServerPlayer player, Announcement announcement) {
        MutableComponent message = Component.literal("Announcement: ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(announcement.getText()).withStyle(ChatFormatting.YELLOW));
        if (!announcement.getLinkUrl().isBlank()) {
            message = message.append(" ").append(Component.literal("[Learn more]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(announcement.getLinkUrl())))));
        }
        player.sendSystemMessage(message);
    }

    private static void request(ServerPlayer player, boolean includeRead) {
        GameplayGrpcService.getAnnouncements(player.getUUID().toString(), includeRead)
                .thenAccept(response -> {
                    MinecraftServer server = player.level().getServer();
                    if (server == null) return;
                    server.execute(() -> {
                        if (player.hasDisconnected()) return;
                        List<Announcement> announcements = response.getAnnouncementsList();
                        if (announcements.isEmpty()) {
                            if (includeRead) player.sendSystemMessage(Component.literal("There are no active announcements.")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        announcements.forEach(announcement -> send(player, announcement));
                        GameplayGrpcService.markAnnouncementsRead(
                                player.getUUID().toString(),
                                announcements.stream().map(Announcement::getId).toList()
                        ).exceptionally(error -> {
                            MainMod.LOGGER.debug("Could not mark announcements read for {}", player.getName().getString(), error);
                            return null;
                        });
                    });
                })
                .exceptionally(error -> {
                    MainMod.LOGGER.debug("Could not load announcements for {}", player.getName().getString(), error);
                    if (includeRead) {
                        MinecraftServer server = player.level().getServer();
                        if (server != null) server.execute(() -> player.sendSystemMessage(
                                Component.literal("Announcements are unavailable. Try again.").withStyle(ChatFormatting.RED)
                        ));
                    }
                    return null;
                });
    }
}
