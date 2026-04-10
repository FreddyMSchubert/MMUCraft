package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseOnBlockCallbackCharm;

import java.util.ArrayList;
import java.util.List;

public final class FarmingBootsCharm implements Charm, UseOnBlockCallbackCharm
{
    @Override
    public InteractionResult onUseOnBlock(ItemStack stack, ServerPlayer player, ServerLevel level, InteractionHand interactionHand, BlockHitResult blockHitResult, int charmLevel)
    {
        BlockPos originPos = blockHitResult.getBlockPos();
        BlockState originState = level.getBlockState(originPos);
        ItemStack heldStack = player.getItemInHand(interactionHand);
        if (!(heldStack.getItem() instanceof HoeItem)) return InteractionResult.PASS;
        if (!isHarvestableCrop(originState)) return InteractionResult.PASS;

        boolean harvestedAny = false;
        for (BlockPos targetPos : getTargetPositions(originPos, charmLevel)) {
            if (harvestAndReplant(level, player, targetPos, originPos, heldStack)) {
                harvestedAny = true;

                heldStack.hurtAndBreak(1, player, interactionHand);
                if (heldStack.isEmpty()) {
                    break;
                }
            }
        }

        return harvestedAny ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean isHarvestableCrop(BlockState state)
    {
        return state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
    }

    private static List<BlockPos> getTargetPositions(BlockPos originPos, int charmLevel)
    {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(originPos);

        if (charmLevel <= 1) {
            return positions;
        }

        // Level map:
        // 1 -> single block
        // 2 -> manhattan radius 1  (cross)
        // 3 -> 3x3 square
        // 4 -> manhattan radius 2
        // 5 -> 5x5 square
        // 6 -> manhattan radius 3
        // 7 -> 7x7 square
        // 8 -> manhattan radius 4
        // 9 -> 9x9 square
        if ((charmLevel & 1) == 0) {
            int radius = charmLevel / 2;

            for (int dx = -radius; dx <= radius; dx++) {
                int maxDz = radius - Math.abs(dx);

                for (int dz = -maxDz; dz <= maxDz; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    positions.add(originPos.offset(dx, 0, dz));
                }
            }
        } else {
            int radius = charmLevel / 2;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    positions.add(originPos.offset(dx, 0, dz));
                }
            }
        }

        return positions;
    }

    private static boolean harvestAndReplant(ServerLevel level, ServerPlayer player, BlockPos targetPos, BlockPos dropPos, ItemStack tool)
    {
        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock cropBlock)) return false;
        if (!cropBlock.isMaxAge(state)) return false;

        BlockState replantedState = cropBlock.getStateForAge(0);
        if (!replantedState.canSurvive(level, targetPos)) return false;

        List<ItemStack> drops = Block.getDrops(state, level, targetPos, null, player, tool);

        // consume one seed / replant item from the drops so replanting is not free
        ItemStack replantCost = cropBlock.getCloneItemStack(level, targetPos, state, false);
        if (!replantCost.isEmpty()) {
            shrinkFirstMatchingStack(drops, replantCost);
        }

        level.levelEvent(2001, targetPos, Block.getId(state)); // block break particles/sound
        level.setBlock(targetPos, replantedState, 3);

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, dropPos, drop);
            }
        }

        return true;
    }

    private static void shrinkFirstMatchingStack(List<ItemStack> drops, ItemStack match)
    {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(drop, match)) continue;

            drop.shrink(1);
            break;
        }

        drops.removeIf(ItemStack::isEmpty);
    }
}
