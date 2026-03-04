package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface Charm
{
    String id();

    // called when charm is instantiated, useful e.g. to add attribute modifiers to the itemstack
    @NotNull ItemStack onCreation(ItemStack stack);
}
