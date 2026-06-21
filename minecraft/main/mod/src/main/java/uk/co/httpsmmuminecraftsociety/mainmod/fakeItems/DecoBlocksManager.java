package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.DecoBlockItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

public final class DecoBlocksManager {
    private DecoBlocksManager() {}

    public static final String DECO_BLOCK_FRAME_TAG = "mainmod_deco_block";
    private static final String DECO_BLOCK_STASHED_CUSTOM_NAME_TAG = "mainmod_deco_block_custom_name";

    public static @Nullable InteractionResult onUseItemOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return null;
        }

        BlockHitResult hitResult = new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                context.getClickedPos(),
                context.isInside()
        );
        InteractionResult result = onUseBlock(player, context.getLevel(), context.getHand(), hitResult);
        return result == InteractionResult.PASS ? null : result;
    }

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
        frame.setItem(hideCustomNameForFrame(stack.copyWithCount(1)), false);

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

    public static ItemStack restoreCustomNameFromFrame(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.read(DECO_BLOCK_STASHED_CUSTOM_NAME_TAG, ComponentSerialization.CODEC)
                .ifPresent(customName -> {
                    stack.set(DataComponents.CUSTOM_NAME, customName);
                    tag.remove(DECO_BLOCK_STASHED_CUSTOM_NAME_TAG);

                    if (tag.isEmpty()) {
                        stack.remove(DataComponents.CUSTOM_DATA);
                    } else {
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    }
                });

        return stack;
    }

    private static ItemStack hideCustomNameForFrame(ItemStack stack) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return stack;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(DECO_BLOCK_STASHED_CUSTOM_NAME_TAG, ComponentSerialization.CODEC, customName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.remove(DataComponents.CUSTOM_NAME);
        return stack;
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
