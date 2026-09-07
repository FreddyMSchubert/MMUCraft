package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.server.MinecraftServer;
import uk.co.httpsmmuminecraftsociety.mainmod.Announcements;

final class GameplayAnnouncementOperations {
    private GameplayAnnouncementOperations() {}

    static BroadcastAnnouncementResponse broadcastOnMainThread(BroadcastAnnouncementRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        Announcement announcement = request.getAnnouncement();
        BroadcastAnnouncementResponse.Builder response = BroadcastAnnouncementResponse.newBuilder();
        server.getPlayerList().getPlayers().forEach(player -> {
            Announcements.send(player, announcement);
            response.addMinecraftUuids(player.getUUID().toString());
        });
        return response.build();
    }
}
