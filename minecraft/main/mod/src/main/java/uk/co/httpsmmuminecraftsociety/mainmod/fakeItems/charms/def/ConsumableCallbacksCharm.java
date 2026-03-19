package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface ConsumableCallbacksCharm
{
    float getUseDurationTicks();

    void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks);
    ItemStack onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks);
}
