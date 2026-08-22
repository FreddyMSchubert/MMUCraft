package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
final class GameplayClaimOperations {
    private GameplayClaimOperations() {}

    static GetCurrentClaimChunkResponse getCurrentClaimChunkOnMainThread(GetCurrentClaimChunkRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GetCurrentClaimChunkResponse.newBuilder()
                    .setOnline(false)
                    .setMessage("You have to be online on the server to claim a chunk.")
                    .build();
        }

        ChunkPos chunk = player.chunkPosition();
        return GetCurrentClaimChunkResponse.newBuilder()
                .setOnline(true)
                .setDimension(player.level().dimension().identifier().toString())
                .setChunkX(chunk.x())
                .setChunkZ(chunk.z())
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage("Current chunk loaded.")
                .build();
    }

    static PurchaseClaimResponse purchaseClaimOnMainThread(PurchaseClaimRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to buy a claim.")
                    .build();
        }

        ChunkPos current = player.chunkPosition();
        String dimension = player.level().dimension().identifier().toString();
        if (!dimension.equals(request.getDimension())
                || current.x() != request.getChunkX()
                || current.z() != request.getChunkZ()) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Stay in the displayed chunk until the purchase finishes.")
                    .build();
        }

        int price = request.getPriceDabloons();
        if (price <= 0) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("The claim price is invalid.")
                    .build();
        }

        int balance = MoneyHelper.GetBalance(player);
        if (balance < price || !MoneyHelper.ReduceMoney(player, price)) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("You need " + price + " dabloons to buy this claim.")
                    .build();
        }

        MoneyHelper.SendBalanceMessage(player, "Chunk claimed for " + price + " dabloons.");
        return PurchaseClaimResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage("Chunk claimed for " + price + " dabloons.")
                .build();
    }
}
