package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface Charm
{
    public String id();

    // called when charm is instantiated, useful e.g. to add attribute modifiers to the itemstack
    public ItemStack onCreation(ItemStack stack);

    // called every tick when the charm is equipped, useful for ticking charm effects
    // add a custom data compound "charm-ontickcallback" set to true on the itemstack onCreation to subscribe to this while equipped or held
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level);
}
