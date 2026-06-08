package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.DecoBlockItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

public final class DecoBlocksManager {
    private DecoBlocksManager() {}

    public static final String DECO_BLOCK_FRAME_TAG = "mainmod_deco_block";

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult)
    {
        ItemStack stack = player.getItemInHand(hand);
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        if (fakeItem == null) {
            return InteractionResult.PASS;
        }

        DecoBlockItemFeature decoBlock = fakeItem.getFeature(DecoBlockItemFeature.class);
        if (decoBlock == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Direction face = hitResult.getDirection();
        if (!decoBlock.canPlaceOn(face)) {
            return rejectPlacement(player, hand);
        }

        BlockPos placePos = hitResult.getBlockPos().relative(face);
        if (!level.isInsideBuildHeight(placePos) || !player.mayUseItemAt(placePos, face, stack)) {
            return rejectPlacement(player, hand);
        }

        ItemFrame frame = new ItemFrame(level, placePos, face);
        frame.setInvisible(true);
        frame.addTag(DECO_BLOCK_FRAME_TAG);
        frame.setItem(stack.copyWithCount(1), false);

        if (!frame.survives()) {
            return rejectPlacement(player, hand);
        }

        frame.playPlacementSound();
        level.gameEvent(player, GameEvent.ENTITY_PLACE, frame.position());
        if (!level.addFreshEntity(frame)) {
            return rejectPlacement(player, hand);
        }

        stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult rejectPlacement(Player player, InteractionHand hand) {
        syncHeldSlot(player, hand);
        return InteractionResult.FAIL;
    }

    private static void syncHeldSlot(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        int slot = hand == InteractionHand.MAIN_HAND
                ? serverPlayer.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;

        serverPlayer.connection.send(serverPlayer.getInventory().createInventoryUpdatePacket(slot));
    }
}
