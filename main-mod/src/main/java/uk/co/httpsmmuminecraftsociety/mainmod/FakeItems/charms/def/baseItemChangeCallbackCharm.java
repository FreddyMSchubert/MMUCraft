package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface baseItemChangeCallbackCharm extends Charm
{
    @NotNull ItemStack enableEffectForItem(ItemStack stack);
    @NotNull ItemStack disableEffectForItem(ItemStack stack);
}
