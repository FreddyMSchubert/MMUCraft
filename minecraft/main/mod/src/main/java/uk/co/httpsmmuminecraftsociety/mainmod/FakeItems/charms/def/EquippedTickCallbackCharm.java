package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface EquippedTickCallbackCharm
{
    ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level);
}
