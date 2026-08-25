package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public interface AttackBlockCallbackCharm extends Charm {
    InteractionResult onAttackBlock(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockPos pos,
            Direction direction,
            int charmLevel
    );
}
