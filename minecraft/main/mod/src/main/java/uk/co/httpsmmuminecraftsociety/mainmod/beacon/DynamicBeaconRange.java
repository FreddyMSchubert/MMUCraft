package uk.co.httpsmmuminecraftsociety.mainmod.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

public final class DynamicBeaconRange {
    public static final long RECALCULATE_INTERVAL_TICKS = 80L;

    private static final int MAX_BEACON_LEVELS = 4;
    private static final double MAX_DYNAMIC_RANGE = 200.0D;

    private DynamicBeaconRange() {}

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!serverPlayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        if (!serverLevel.getBlockState(pos).is(Blocks.BEACON)) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        double range = blockEntity instanceof DynamicBeaconRangeHolder holder
                ? recalculate(serverLevel, pos, holder)
                : computeRange(serverLevel, pos);

        serverPlayer.sendSystemMessage(Component.literal("Beacon range: " + formatRange(range) + " blocks."));
        return InteractionResult.SUCCESS;
    }

    public static double recalculate(Level level, BlockPos pos, DynamicBeaconRangeHolder holder) {
        double range = computeRange(level, pos);
        holder.mainmod$setDynamicBeaconRange(range);
        return range;
    }

    public static double computeRange(Level level, BlockPos beaconPos) {
        return Math.min(computeRawRange(level, beaconPos), MAX_DYNAMIC_RANGE);
    }

    private static double computeRawRange(Level level, BlockPos beaconPos) {
        double range = 0.0D;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int layer = 1; layer <= MAX_BEACON_LEVELS; layer++) {
            int y = beaconPos.getY() - layer;
            if (y < level.getMinY()) {
                return range;
            }

            double layerRange = 0.0D;
            for (int x = beaconPos.getX() - layer; x <= beaconPos.getX() + layer; x++) {
                for (int z = beaconPos.getZ() - layer; z <= beaconPos.getZ() + layer; z++) {
                    BlockState state = level.getBlockState(mutablePos.set(x, y, z));
                    if (!state.is(BlockTags.BEACON_BASE_BLOCKS)) {
                        return range;
                    }
                    layerRange += rangeForBaseBlock(state);
                }
            }
            range += layerRange;
        }

        return range;
    }

    private static double rangeForBaseBlock(BlockState state) {
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.EMERALD_BLOCK)) {
            return 0.3D;
        }
        if (state.is(Blocks.GOLD_BLOCK)) {
            return 0.5D;
        }
        if (state.is(Blocks.DIAMOND_BLOCK)) {
            return 2.5D;
        }
        if (state.is(Blocks.NETHERITE_BLOCK)) {
            return 5.0D;
        }
        return 0.0D;
    }

    private static String formatRange(double range) {
        double rounded = Math.rint(range);
        if (Math.abs(range - rounded) < 0.05D) {
            return Integer.toString((int) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", range);
    }
}
