package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public interface UseOnBlockCallbackCharm extends Charm
{
    InteractionResult onUseOnBlock(ItemStack stack, ServerPlayer player, ServerLevel level, InteractionHand interactionHand, BlockHitResult blockHitResult, int charmLevel);
}
