package dev.freddy.killshift;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

final class NearbyRespawn {
    private static final int ATTEMPTS = 96;

    private NearbyRespawn() { }

    static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        if (alive) return;
        ServerLevel level = oldPlayer.level();
        int radius = EventApi.isMember(oldPlayer) ? 100 : 200;

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.TAU;
            double distance = Math.sqrt(player.getRandom().nextDouble()) * radius;
            int x = attempt == 0 ? oldPlayer.blockPosition().getX()
                    : (int) Math.floor(oldPlayer.getX() + Math.cos(angle) * distance);
            int z = attempt == 0 ? oldPlayer.blockPosition().getZ()
                    : (int) Math.floor(oldPlayer.getZ() + Math.sin(angle) * distance);
            double dx = x + 0.5 - oldPlayer.getX();
            double dz = z + 0.5 - oldPlayer.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            int startY = oldPlayer.blockPosition().getY() + 16;
            if (!level.dimensionType().hasCeiling()) {
                startY = Math.max(startY, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2);
            }
            startY = Math.clamp(startY, level.getMinY() + 2, level.getMaxY() - 2);
            for (int y = startY; y > level.getMinY(); y--) {
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos floor = feet.below();
                if (!level.getWorldBorder().isWithinBounds(feet)
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        || level.getBlockState(floor).is(Blocks.MAGMA_BLOCK)) continue;
                AABB box = player.getDimensions(player.getPose()).makeBoundingBox(
                        x + 0.5, y, z + 0.5);
                if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(box)
                        && player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(),
                                player.getYRot(), player.getXRot(), false)) return;
            }
        }
        player.sendSystemMessage(Component.literal("No safe respawn spot was found within "
                + radius + " blocks. Minecraft used your normal spawn."));
    }
}
