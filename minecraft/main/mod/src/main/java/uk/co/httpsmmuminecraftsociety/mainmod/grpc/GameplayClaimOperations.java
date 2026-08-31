package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.BlockPos;
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
                    .setMessage("Join the Minecraft server and stand in the chunk you want to claim, then try again.")
                    .build();
        }

        ChunkPos chunk = player.chunkPosition();
        BlockPos position = player.blockPosition();
        return GetCurrentClaimChunkResponse.newBuilder()
                .setOnline(true)
                .setDimension(player.level().dimension().identifier().toString())
                .setChunkX(chunk.x())
                .setChunkZ(chunk.z())
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setBlockX(position.getX())
                .setBlockY(position.getY())
                .setBlockZ(position.getZ())
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
                    .setMessage("Join the Minecraft server and remain in the selected chunk until the claim purchase finishes.")
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
                    .setMessage(
                            "This claim costs " + price + " dabloons, but your balance is " + balance + ". Earn "
                                    + Math.max(0, price - balance) + " more and try again."
                    )
                    .build();
        }

        MoneyHelper.SendBalanceMessage(player, "Chunk claimed for " + price + " dabloons.");
        return PurchaseClaimResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage(
                        "Chunk claimed for " + price + " dabloons. Your remaining balance is "
                                + MoneyHelper.GetBalance(player) + "."
                )
                .build();
    }
}
