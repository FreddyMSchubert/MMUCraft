package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class BoostFrames {
    static final int MAX_DIAMETER = 20;

    static @Nullable Vec3 crossedFrame(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 movement = to.subtract(from);
        // Do not search along teleports or large position corrections.
        if (movement.lengthSqr() > 64) return null;
        for (Direction.Axis normal : Direction.Axis.values()) {
            double start = normal.choose(from.x, from.y, from.z);
            double delta = normal.choose(movement.x, movement.y, movement.z);
            if (Math.abs(delta) < 1.0E-7) continue;
            double end = start + delta;
            for (int plane = Mth.floor(Math.min(start, end)); plane <= Mth.floor(Math.max(start, end)); plane++) {
                double fraction = (plane + 0.5 - start) / delta;
                if (fraction <= 0 || fraction > 1) continue;
                BlockPos inside = BlockPos.containing(from.add(movement.scale(fraction)));
                Direction first = Direction.get(Direction.AxisDirection.POSITIVE, Direction.Axis.values()[(normal.ordinal() + 1) % 3]);
                Direction second = Direction.get(Direction.AxisDirection.POSITIVE, Direction.Axis.values()[(normal.ordinal() + 2) % 3]);
                if (isFrame(level, inside, first, second)) {
                    Direction direction = Direction.get(delta > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, normal);
                    return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                }
            }
        }
        return null;
    }

    private static boolean isFrame(ServerLevel level, BlockPos inside, Direction first, Direction second) {
        if (!level.hasChunkAt(inside) || !level.getBlockState(inside).isAir()) return false;
        int left = boundary(level, inside, first.getOpposite());
        int right = boundary(level, inside, first);
        int bottom = boundary(level, inside, second.getOpposite());
        int top = boundary(level, inside, second);
        if (left < 1 || right < 1 || bottom < 1 || top < 1
                || left + right + 1 > MAX_DIAMETER || bottom + top + 1 > MAX_DIAMETER) return false;
        for (int x = -left; x <= right; x++) {
            if (!isAmethyst(level, inside.relative(first, x).relative(second, -bottom))
                    || !isAmethyst(level, inside.relative(first, x).relative(second, top))) return false;
        }
        for (int y = -bottom; y <= top; y++) {
            if (!isAmethyst(level, inside.relative(first, -left).relative(second, y))
                    || !isAmethyst(level, inside.relative(first, right).relative(second, y))) return false;
        }
        return true;
    }

    private static int boundary(ServerLevel level, BlockPos inside, Direction direction) {
        for (int distance = 1; distance < MAX_DIAMETER - 1; distance++) {
            BlockPos pos = inside.relative(direction, distance);
            if (!level.hasChunkAt(pos)) return -1;
            var block = level.getBlockState(pos);
            if (block.is(Blocks.AMETHYST_BLOCK)) return distance;
            if (!block.isAir()) return -1;
        }
        return -1;
    }

    private static boolean isAmethyst(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK);
    }
}
