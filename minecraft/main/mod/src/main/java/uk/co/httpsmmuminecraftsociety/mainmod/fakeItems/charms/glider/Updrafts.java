package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

final class Updrafts {
    static final int FIRE_RANGE = 60;
    static final int LAVA_RANGE = 80;
    static final int SOUL_FIRE_RANGE = 100;
    static final double ACCELERATION = 0.05;
    static final double MAX_UPWARD_SPEED = 1.0;

    static double liftAt(ServerPlayer player) {
        var level = player.level();
        BlockPos feet = BlockPos.containing(player.getX(), player.getBoundingBox().minY, player.getZ());
        int maxRange = Math.max(FIRE_RANGE, Math.max(LAVA_RANGE, SOUL_FIRE_RANGE));
        for (int distance = 0; distance <= maxRange && feet.getY() - distance >= level.getMinY(); distance++) {
            BlockPos pos = feet.below(distance);
            BlockState block = level.getBlockState(pos);
            int range = level.getFluidState(pos).is(FluidTags.LAVA) ? LAVA_RANGE : heatRange(block);
            if (range > 0) {
                if (distance >= range) return 0;
                int ceilingDistance = range - distance;
                BlockPos head = BlockPos.containing(player.getX(), player.getBoundingBox().maxY, player.getZ());
                for (int above = 0; above < ceilingDistance; above++) {
                    if (!level.getBlockState(head.above(above)).isAir()) {
                        ceilingDistance = above;
                        break;
                    }
                }
                int end = Math.min(range, distance + ceilingDistance);
                return end <= distance ? 0 : ACCELERATION * (1.0 - (double) distance / end);
            }
            if (!block.isAir()) return 0;
        }
        return 0;
    }

    static int heatRange(BlockState block) {
        if (block.is(Blocks.FIRE)) return FIRE_RANGE;
        if (block.is(Blocks.SOUL_FIRE)) return SOUL_FIRE_RANGE;
        if ((block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE)) && block.getValue(CampfireBlock.LIT)) {
            return block.is(Blocks.SOUL_CAMPFIRE) ? SOUL_FIRE_RANGE : FIRE_RANGE;
        }
        return 0;
    }
}
