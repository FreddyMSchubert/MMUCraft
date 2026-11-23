package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

import java.util.concurrent.ThreadLocalRandom;

public final class RespawnPositionFinder {
    private static final int MAX_SEARCHES = 64;
    private static final int DRY_SEARCHES = 8;
    private static final int RING_SEARCHES = 12;

    private RespawnPositionFinder() {}

    public static BlockPos find(ServerPlayer player, ServerLevel level, BlockPos origin) {
        for (int attempt = 0; attempt < MAX_SEARCHES; attempt++) {
            BlockPos candidate = locate(player, level, origin, attempt < DRY_SEARCHES, attempt < RING_SEARCHES);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static BlockPos locate(ServerPlayer player, ServerLevel level, BlockPos origin,
                                   boolean requireDryLand, boolean usePreferredDistance) {
        double maxDistance = level.getServer().getPlayerList().getViewDistance()
                * (PlayerStatsSync.isMember(player) ? 8.0 : 16.0);
        double minDistance = usePreferredDistance ? maxDistance * 0.5 : 0.0;
        double angle = ThreadLocalRandom.current().nextDouble(Math.TAU);
        double distance = ThreadLocalRandom.current().nextDouble(minDistance, maxDistance);
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
        BlockPos found = requireDryLand || level.dimensionType().hasCeiling()
                ? PlayerSpawnFinder.getSpawnPosInChunk(level,
                new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
                : new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (found == null || found.getY() <= level.getMinY() || found.getY() + 1 >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(found)
                || !level.getBlockState(found).getBlock().isPossibleToRespawnInThis(level.getBlockState(found))
                || !level.getBlockState(found.above()).getBlock().isPossibleToRespawnInThis(level.getBlockState(found.above())))
            return null;
        BlockPos floor = found.below();
        if (level.getFluidState(floor).is(FluidTags.LAVA)
                || (!level.getFluidState(floor).is(FluidTags.WATER)
                && !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP))) return null;
        long dx = found.getX() - origin.getX();
        long dz = found.getZ() - origin.getZ();
        double squared = dx * dx + dz * dz;
        return squared >= minDistance * minDistance && squared <= maxDistance * maxDistance ? found : null;
    }
}
