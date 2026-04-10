package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AfterBlockBreakCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SawBeltCharm implements Charm, AfterBlockBreakCallbackCharm
{
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();

    private static int getMaxExtraBlocksForLevel(int level)
    {
        return (int)Math.pow(2, level);
    }

    @Override
    public void afterBlockBreak(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            BlockPos originPos,
            BlockState brokenState,
            int charmLevel
    ) {
        if (!player.isShiftKeyDown()) return;

        ItemStack heldTool = player.getMainHandItem();
        if (heldTool.isEmpty()) return;
        if (!(heldTool.getItem() instanceof AxeItem)) return;

        if (!isTreeLog(brokenState)) return;

        // prevent recursion from recursive blockbreak fires triggered by this code
        UUID uuid = player.getUUID();
        if (!ACTIVE_PLAYERS.add(uuid)) return;

        try {
            fellTree(player, level, originPos, charmLevel);
        } finally {
            ACTIVE_PLAYERS.remove(uuid);
        }
    }

    private static boolean isTreeLog(BlockState state)
    {
        return state.is(BlockTags.LOGS);
    }

    private static void fellTree(ServerPlayer player, ServerLevel level, BlockPos originPos, int charmLevel)
    {
        int remaining = getMaxExtraBlocksForLevel(charmLevel);
        if (remaining <= 0) return;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(originPos);
        visited.add(originPos);

        while (!queue.isEmpty() && remaining > 0) {
            ItemStack heldTool = player.getMainHandItem();
            if (heldTool.isEmpty()) break;
            if (!(heldTool.getItem() instanceof AxeItem)) break;

            BlockPos current = queue.removeFirst();

            // 26-neighbour search catches branches + 2x2 trunks much better than 6 faces only
            for (int dx = -1; dx <= 1 && remaining > 0; dx++) {
                for (int dy = -1; dy <= 1 && remaining > 0; dy++) {
                    for (int dz = -1; dz <= 1 && remaining > 0; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos nextPos = current.offset(dx, dy, dz);
                        if (!visited.add(nextPos)) continue;

                        BlockState nextState = level.getBlockState(nextPos);
                        if (!isTreeLog(nextState)) continue;

                        if (!player.gameMode.destroyBlock(nextPos)) continue;

                        remaining--;
                        queue.addLast(nextPos);
                    }
                }
            }
        }
    }
}
