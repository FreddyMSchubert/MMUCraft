package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class Updrafts {
    static final int FIRE_RANGE = 60;
    static final int LAVA_RANGE = 80;
    static final int SOUL_FIRE_RANGE = 100;
    static final double SOURCE_ACCELERATION = 0.15;
    static final double TOP_ACCELERATION = 0.01;
    static final double MAX_UPWARD_SPEED = 1.0;
    static final int CARRY_TICKS = 20;

    record Updraft(int sourceY, int ceilingY, int expiresAt) {
        double liftAt(double feetY, int tick) {
            if (tick >= expiresAt || feetY >= ceilingY || ceilingY <= sourceY) return 0;
            double remainingFraction = Math.min(1.0, (ceilingY - feetY) / (ceilingY - sourceY));
            return TOP_ACCELERATION
                    + (SOURCE_ACCELERATION - TOP_ACCELERATION) * remainingFraction * remainingFraction * remainingFraction;
        }
    }

    static @Nullable Updraft findAt(ServerPlayer player) {
        var level = player.level();
        BlockPos feet = BlockPos.containing(player.getX(), player.getBoundingBox().minY, player.getZ());
        int maxRange = Math.max(FIRE_RANGE, Math.max(LAVA_RANGE, SOUL_FIRE_RANGE));
        for (int distance = 0; distance <= maxRange && feet.getY() - distance >= level.getMinY(); distance++) {
            BlockPos pos = feet.below(distance);
            BlockState block = level.getBlockState(pos);
            int range = level.getFluidState(pos).is(FluidTags.LAVA) ? LAVA_RANGE : heatRange(block);
            if (range > 0) {
                if (distance >= range) return null;
                int ceilingDistance = range - distance;
                BlockPos head = BlockPos.containing(player.getX(), player.getBoundingBox().maxY, player.getZ());
                for (int above = 0; above < ceilingDistance; above++) {
                    if (!level.getBlockState(head.above(above)).isAir()) {
                        ceilingDistance = above;
                        break;
                    }
                }
                int end = Math.min(range, distance + ceilingDistance);
                return end <= distance ? null : new Updraft(pos.getY(), pos.getY() + end, player.tickCount + CARRY_TICKS);
            }
            if (!block.isAir()) return null;
        }
        return null;
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
