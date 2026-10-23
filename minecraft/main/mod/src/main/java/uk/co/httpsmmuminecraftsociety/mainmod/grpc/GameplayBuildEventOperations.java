package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

final class GameplayBuildEventOperations {
    private GameplayBuildEventOperations() {}

    static TeleportBuildVisitorResponse teleportOnMainThread(TeleportBuildVisitorRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return TeleportBuildVisitorResponse.newBuilder().setMessage("Join the main server first.").build();
        }

        if (request.getCheckCurrentArea()) {
            ChunkPos chunk = player.chunkPosition();
            if (!player.level().dimension().identifier().toString().equals(request.getCurrentDimension())
                    || chunk.x() < request.getMinChunkX() || chunk.x() > request.getMaxChunkX()
                    || chunk.z() < request.getMinChunkZ() || chunk.z() > request.getMaxChunkZ()) {
                return TeleportBuildVisitorResponse.newBuilder().setOnline(true).setLeftArea(true)
                        .setMessage("You left the build area, so the return warp expired.").build();
            }
        }

        Identifier destinationId = Identifier.tryParse(request.getDimension());
        ServerLevel destination = destinationId == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, destinationId));
        Vec3 target = new Vec3(request.getX(), request.getY(), request.getZ());
        if (destination == null || !Double.isFinite(target.x) || !Double.isFinite(target.y)
                || !Double.isFinite(target.z) || !Float.isFinite(request.getYaw())
                || !Float.isFinite(request.getPitch())
                || target.y < destination.getMinY() || target.y >= destination.getMaxY()
                || !destination.getWorldBorder().isWithinBounds(BlockPos.containing(target))) {
            return TeleportBuildVisitorResponse.newBuilder().setOnline(true)
                    .setMessage("The destination is outside the available world.").build();
        }

        Vec3 origin = player.position();
        String originDimension = player.level().dimension().identifier().toString();
        float originYaw = player.getYRot();
        float originPitch = player.getXRot();
        boolean moved = player.teleport(new TeleportTransition(
                destination, target, Vec3.ZERO, request.getYaw(), request.getPitch(),
                Set.of(), TeleportTransition.DO_NOTHING
        )) != null;
        return TeleportBuildVisitorResponse.newBuilder()
                .setOnline(true)
                .setMoved(moved)
                .setMessage(moved ? "Warp complete." : "The warp failed. Please try again.")
                .setOriginDimension(originDimension)
                .setOriginX(origin.x)
                .setOriginY(origin.y)
                .setOriginZ(origin.z)
                .setOriginYaw(originYaw)
                .setOriginPitch(originPitch)
                .build();
    }
}
