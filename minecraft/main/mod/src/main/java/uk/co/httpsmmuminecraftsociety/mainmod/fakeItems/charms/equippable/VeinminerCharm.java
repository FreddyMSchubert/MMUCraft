package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModBlockTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AfterBlockBreakCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinminerCharm implements Charm, AfterBlockBreakCallbackCharm
{
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();

    private static int getMaxExtraBlocksForLevel(int level)
    {
        return level;
    }

    @Override
    public void afterBlockBreak(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos originPos, BlockState brokenState, int charmLevel)
    {
        // prevent recursion when our own extra breaks trigger the same AFTER event again
        UUID uuid = player.getUUID();
        if (!ACTIVE_PLAYERS.add(uuid)) return;

        try {
            veinmine(stack, player, level, originPos, brokenState, charmLevel);
        } finally {
            ACTIVE_PLAYERS.remove(uuid);
        }
    }

    private static void veinmine(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos originPos, BlockState originState, int charmLevel)
    {
        if (originState.isAir()) return;
        if (originState.hasBlockEntity()) return;
        if (!originState.is(ModBlockTagProvider.VEIN_MINEABLE_BLOCKS)) return;

        int remaining = getMaxExtraBlocksForLevel(charmLevel);
        if (remaining <= 0) return;

        Block targetBlock = originState.getBlock();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(originPos);
        visited.add(originPos);

        while (!queue.isEmpty() && remaining > 0) {
            BlockPos current = queue.removeFirst();

            for (Direction direction : Direction.values()) {
                if (remaining <= 0) break;

                BlockPos nextPos = current.relative(direction);
                if (!visited.add(nextPos)) continue;

                BlockState nextState = level.getBlockState(nextPos);

                if (nextState.isAir()) continue;
                if (nextState.hasBlockEntity()) continue;
                if (nextState.getBlock() != targetBlock) continue;

                if (!player.gameMode.destroyBlock(nextPos)) continue;

                remaining--;
                queue.addLast(nextPos);
            }
        }
    }
}
