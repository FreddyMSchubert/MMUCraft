package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface BaseItemChangeCallbackCharm extends Charm
{
    @NotNull ItemStack enableEffectForItem(ItemStack stack);
    @NotNull ItemStack disableEffectForItem(ItemStack stack);
}
