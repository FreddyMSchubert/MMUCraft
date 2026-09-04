package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

public class PotionOfDisplacementCharm implements Charm, ConsumableCallbacksCharm
{
    private static final int MAX_ATTEMPTS = 128;

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {
    }

    @Override
    public boolean onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            player.stopUsingItem();
            return false;
        }

        BlockPos pos = findSafeTeleportPos(level, player.blockPosition(), PlayerStatsSync.isMember(player) ? 15_000 : 7_500);

        TeleportPotionUtils.teleportWithCompanions(
                "displacement",
                player,
                level,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );

        player.fallDistance = 0.0F;
        stack.consume(1, player);
        return true;
    }

    private BlockPos findSafeTeleportPos(ServerLevel level, BlockPos fallback, int radius)
    {
        for (int i = 0; i < MAX_ATTEMPTS; i++)
        {
            BlockPos pos = tryFindPosOnce(level, fallback, radius);
            if (pos != null)
            {
                return pos;
            }
        }

        return fallback;
    }

    private BlockPos tryFindPosOnce(Level level, BlockPos origin, int radius)
    {
        WorldBorder border = level.getWorldBorder();

        int minX = Math.max(Mth.ceil(border.getMinX()), radiusMin(origin.getX(), radius));
        int maxX = Math.min(Mth.floor(border.getMaxX() - 1.0D), radiusMax(origin.getX(), radius));
        int minZ = Math.max(Mth.ceil(border.getMinZ()), radiusMin(origin.getZ(), radius));
        int maxZ = Math.min(Mth.floor(border.getMaxZ() - 1.0D), radiusMax(origin.getZ(), radius));

        if (minX > maxX || minZ > maxZ)
        {
            return null;
        }

        int x = Mth.nextInt(level.getRandom(), minX, maxX);
        int z = Mth.nextInt(level.getRandom(), minZ, maxZ);

        long dx = (long) x - origin.getX();
        long dz = (long) z - origin.getZ();
        if (dx * dx + dz * dz > (long) radius * radius) return null;

        return possibleSpawnPos(x, z, level);
    }

    private int radiusMin(int origin, int radius) {
        return (int)Math.max(Integer.MIN_VALUE, (long)origin - radius);
    }
    private int radiusMax(int origin, int radius) {
        return (int)Math.min(Integer.MAX_VALUE, (long)origin + radius);
    }

    private BlockPos possibleSpawnPos(int x, int z, Level level)
    {
        int minY = level.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, level.getMaxY(), z);

        // skip nether roof
        if (level.dimension().equals(Level.NETHER)) {
            cursor.setY(126);
        }

        // skip blocks at top (nether roof)
        while (cursor.getY() > minY && !level.getBlockState(cursor).isAir())
        {
            cursor.move(0, -1, 0);
        }

        while (cursor.getY() > minY && level.getBlockState(cursor).isAir())
        {
            cursor.move(0, -1, 0);
        }

        if (cursor.getY() <= minY)
        {
            return null;
        }

        BlockState groundState = level.getBlockState(cursor);
        if (groundState.getFluidState().is(FluidTags.LAVA))
        {
            return null;
        }

        BlockPos spawnPos = cursor.above();
        BlockPos headPos = spawnPos.above();

        if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(headPos).isAir())
        {
            return null;
        }

        return spawnPos.immutable();
    }
}
