package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public interface AttackSwingCallbackCharm extends Charm {
    void onAttackSwing(ItemStack stack, ServerPlayer player, ServerLevel level,
                       InteractionHand hand, int charmLevel);
}
