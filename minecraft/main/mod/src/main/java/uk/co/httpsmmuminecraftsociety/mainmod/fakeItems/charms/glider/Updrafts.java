package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;

final class Updrafts {
    static final double TOP_ACCELERATION = 0.02;

    record HeatSource(int range, double sourceAcceleration, double maxUpwardSpeed, int carryTicks,
                      double nearMissAccelBoost) {}

    static final HeatSource FIRE = new HeatSource(16, 0.12, 0.55, 32, 1.75);
    static final HeatSource CAMPFIRE = new HeatSource(18, 0.145, 0.65, 38, 2.0);
    static final HeatSource LAVA = new HeatSource(32, 0.16, 0.68, 40, 2.1);
    static final HeatSource SOUL_FIRE = new HeatSource(45, 0.165, 0.9, 40, 2.15);
    static final HeatSource SOUL_CAMPFIRE = new HeatSource(50, 0.175, 1.0, 42, 2.25);
    private static final Map<Block, HeatSource> BLOCK_SOURCES = Map.of(
            Blocks.FIRE, FIRE,
            Blocks.CAMPFIRE, CAMPFIRE,
            Blocks.SOUL_FIRE, SOUL_FIRE,
            Blocks.SOUL_CAMPFIRE, SOUL_CAMPFIRE
    );

    record Updraft(int sourceY, double ceilingY, int expiresAt, HeatSource heatSource) {
        double liftAt(double feetY, int tick) {
            if (tick >= expiresAt || feetY >= ceilingY || ceilingY <= sourceY) return 0;
            double remainingFraction = Math.min(1.0, (ceilingY - feetY) / (ceilingY - sourceY));
            double accel = TOP_ACCELERATION
                    + (heatSource.sourceAcceleration() - TOP_ACCELERATION)
                    * remainingFraction * remainingFraction * remainingFraction;
            if (feetY >= sourceY + 1 && feetY < sourceY + 2) {
                accel *= heatSource.nearMissAccelBoost();
            }
            return accel;
        }
    }

    static @Nullable Updraft findAt(ServerPlayer player) {
        return findAt(player.level(), player.position(), player.getBbHeight(), player.tickCount);
    }

    static @Nullable Updraft findAt(BlockGetter level, Vec3 position, double height, int tick) {
        BlockPos feet = BlockPos.containing(position);
        int maxRange = SOUL_CAMPFIRE.range();
        for (int distance = 0; distance <= maxRange && feet.getY() - distance >= level.getMinY(); distance++) {
            BlockPos pos = feet.below(distance);
            BlockState block = level.getBlockState(pos);
            HeatSource heatSource = level.getFluidState(pos).is(FluidTags.LAVA) ? LAVA : heatSource(block);
            if (heatSource != null) {
                if (distance >= heatSource.range()) return null;
                double ceilingY = pos.getY() + heatSource.range();
                BlockPos head = BlockPos.containing(position.x, position.y + height, position.z);
                for (int y = head.getY(); y < ceilingY + height; y++) {
                    if (!level.getBlockState(new BlockPos(head.getX(), y, head.getZ())).isAir()) {
                        ceilingY = y - height;
                        break;
                    }
                }
                return ceilingY <= position.y ? null
                        : new Updraft(pos.getY(), ceilingY, tick + heatSource.carryTicks(), heatSource);
            }
            if (!block.isAir()) return null;
        }
        return null;
    }

    static @Nullable HeatSource heatSource(BlockState block) {
        HeatSource heatSource = BLOCK_SOURCES.get(block.getBlock());
        return heatSource != null && (!(block.getBlock() instanceof CampfireBlock)
                || block.getValue(CampfireBlock.LIT)) ? heatSource : null;
    }
}
