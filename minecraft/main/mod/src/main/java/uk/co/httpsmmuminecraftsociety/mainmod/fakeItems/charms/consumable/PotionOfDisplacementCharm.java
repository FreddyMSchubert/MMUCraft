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
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

import java.util.Set;

public class PotionOfDisplacementCharm implements Charm, ConsumableCallbacksCharm
{
    private static final int MAX_ATTEMPTS = 128;

    @Override
    public String id()
    {
        return "cosmetic-charm-potion-of-displacement";
    }

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks) {
    }

    @Override
    public ItemStack onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks)
    {
        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            player.stopUsingItem();
            return stack;
        }

        BlockPos pos = findSafeTeleportPos(level, player.blockPosition());

        MainMod.LOGGER.info("Teleporting (Portion of Displacement) player " + player.getName().getString() + " from (" + player.getX() + ", " + player.getY() + ", " + player.getZ() + ") to spawn (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ").");

        player.teleportTo(
                level,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                false
        );

        player.fallDistance = 0.0F;
        return stack;
    }

    private BlockPos findSafeTeleportPos(ServerLevel level, BlockPos fallback)
    {
        for (int i = 0; i < MAX_ATTEMPTS; i++)
        {
            BlockPos pos = tryFindPosOnce(level);
            if (pos != null)
            {
                return pos;
            }
        }

        return fallback;
    }

    private BlockPos tryFindPosOnce(Level level)
    {
        WorldBorder border = level.getWorldBorder();

        int minX = Mth.ceil(border.getMinX());
        int maxX = Mth.floor(border.getMaxX() - 1.0D);
        int minZ = Mth.ceil(border.getMinZ());
        int maxZ = Mth.floor(border.getMaxZ() - 1.0D);

        if (minX > maxX || minZ > maxZ)
        {
            return null;
        }

        int x = Mth.nextInt(level.random, minX, maxX);
        int z = Mth.nextInt(level.random, minZ, maxZ);

        return possibleSpawnPos(x, z, level);
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
