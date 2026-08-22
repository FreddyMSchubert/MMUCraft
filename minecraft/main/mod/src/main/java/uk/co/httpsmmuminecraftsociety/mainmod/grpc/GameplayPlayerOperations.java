package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.Comparator;
import java.util.UUID;
final class GameplayPlayerOperations {
    private GameplayPlayerOperations() {}

    static ApplyPlayerColorResponse applyPlayerColorOnMainThread(ApplyPlayerColorRequest request) {
        if (!request.getColorHex().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid player color");
        }
        UUID playerId = UUID.fromString(request.getMinecraftUuid());
        int color = Integer.parseInt(request.getColorHex().replace("#", ""), 16);
        ClaimsManager.updateOwnerColor(playerId, color);
        ServerPlayer player = GrpcBridge.minecraftServer().getPlayerList().getPlayer(playerId);
        if (player != null && !player.hasDisconnected()) PlayerStatsSync.applyColor(player, color);
        return ApplyPlayerColorResponse.newBuilder().setApplied(true).build();
    }

    static ApplyPlayerSettingsResponse applyPlayerSettingsOnMainThread(ApplyPlayerSettingsRequest request) {
        UUID playerId = UUID.fromString(request.getMinecraftUuid());
        ServerPlayer player = GrpcBridge.minecraftServer().getPlayerList().getPlayer(playerId);
        if (player != null && !player.hasDisconnected()) {
            PlayerStatsSync.applyShowDeathCounter(player, request.getShowDeathCounter());
        }
        return ApplyPlayerSettingsResponse.newBuilder().setApplied(true).build();
    }

    static GetOnlinePlayersResponse getOnlinePlayersOnMainThread() {
        GetOnlinePlayersResponse.Builder response = GetOnlinePlayersResponse.newBuilder();
        GrpcBridge.minecraftServer().getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(player -> response.addPlayers(OnlinePlayer.newBuilder()
                        .setMinecraftUsername(player.getName().getString())
                        .setMinecraftUuid(player.getUUID().toString())));
        return response.build();
    }
}
