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
            double distance = Math.sqrt(player.getRandom().nextDouble()
                    * (radius * radius - 16 * 16) + 16 * 16);
            int x = (int) Math.floor(oldPlayer.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(oldPlayer.getZ() + Math.sin(angle) * distance);
            double dx = x + 0.5 - oldPlayer.getX();
            double dz = z + 0.5 - oldPlayer.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int lowestY = level.dimensionType().hasCeiling() ? level.getMinY() + 1 : surfaceY - 1;
            for (int y = surfaceY + 1; y >= lowestY; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos floor = feet.below();
                if (!level.getWorldBorder().isWithinBounds(feet)
                        || y <= level.getMinY() || y + 1 >= level.getMaxY()
                        || (!level.dimensionType().hasCeiling() && !level.canSeeSky(feet))
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        || level.getBlockState(floor).is(Blocks.MAGMA_BLOCK)
                        || level.getBlockState(floor).is(Blocks.BEDROCK)) continue;
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
