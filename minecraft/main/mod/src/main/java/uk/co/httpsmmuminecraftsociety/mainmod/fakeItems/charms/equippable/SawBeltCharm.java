package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AfterBlockBreakCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseOnBlockCallbackCharm;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class SawBeltCharm implements Charm, AfterBlockBreakCallbackCharm, UseOnBlockCallbackCharm
{
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    // One progression step per wood set; the final step retains the old 256-log cap.
    private static final int[] EXTRA_LOGS_BY_LEVEL = {0, 1, 2, 3, 4, 5, 6, 8, 12, 18, 32, 64, 128, 192, 256};

    private static int getMaxExtraBlocksForLevel(int level)
    {
        return EXTRA_LOGS_BY_LEVEL[Math.clamp(level, 0, EXTRA_LOGS_BY_LEVEL.length - 1)];
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
        if (!heldTool.is(ItemTags.AXES)) return;

        if (!isTreeLog(brokenState)) return;

        // prevent recursion from recursive blockbreak fires triggered by this code
        UUID uuid = player.getUUID();
        if (!ACTIVE_PLAYERS.add(uuid)) return;

        try {
            spreadLogs(player, level, originPos, charmLevel, player.gameMode::destroyBlock);
        } finally {
            ACTIVE_PLAYERS.remove(uuid);
        }
    }

    @Override
    public InteractionResult onUseOnBlock(ItemStack stack, ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit, int charmLevel)
    {
        ItemStack axe = player.getMainHandItem();
        BlockPos originPos = hit.getBlockPos();
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()
                || !axe.is(ItemTags.AXES) || player.getCooldowns().isOnCooldown(axe)
                || !isTreeLog(level.getBlockState(originPos))) return InteractionResult.PASS;
        if (!ClaimsManager.canAccess(player, level, originPos) || !player.mayUseItemAt(originPos, hit.getDirection(), axe))
            return InteractionResult.PASS;

        InteractionResult result = axe.useOn(new UseOnContext(player, hand, hit));
        if (!result.consumesAction()) return result;

        spreadLogs(player, level, originPos, charmLevel, pos -> {
            ItemStack heldAxe = player.getMainHandItem();
            if (player.getCooldowns().isOnCooldown(heldAxe) || !ClaimsManager.canAccess(player, level, pos)
                    || !player.mayUseItemAt(pos, hit.getDirection(), heldAxe))
                return false;
            BlockHitResult targetHit = new BlockHitResult(Vec3.atCenterOf(pos), hit.getDirection(), pos, false);
            return heldAxe.useOn(new UseOnContext(player, hand, targetHit)).consumesAction();
        });
        return result;
    }

    private static boolean isTreeLog(BlockState state)
    {
        return state.is(BlockTags.LOGS);
    }

    private static void spreadLogs(ServerPlayer player, ServerLevel level, BlockPos originPos, int charmLevel, Predicate<BlockPos> useLog)
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
            if (!heldTool.is(ItemTags.AXES)) break;

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

                        if (!useLog.test(nextPos)) continue;

                        CharmParticles.atBlock(level, nextPos, ParticleTypes.DUST_PLUME, 3);

                        remaining--;
                        queue.addLast(nextPos);
                    }
                }
            }
        }
    }
}
