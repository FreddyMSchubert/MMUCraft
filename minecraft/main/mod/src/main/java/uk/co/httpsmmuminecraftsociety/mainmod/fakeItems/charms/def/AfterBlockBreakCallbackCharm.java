package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface AfterBlockBreakCallbackCharm extends Charm
{
    void afterBlockBreak(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos pos, BlockState brokenState, int charmLevel);
}
