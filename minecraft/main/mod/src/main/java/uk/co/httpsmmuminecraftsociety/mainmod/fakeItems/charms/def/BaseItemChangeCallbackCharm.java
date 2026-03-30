package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.world.item.ItemStack;

public interface BaseItemChangeCallbackCharm extends Charm
{
    void enableEffectForItem(ItemStack stack, int charmLevel);
    void disableEffectForItem(ItemStack stack, int charmLevel);
}
