package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface Charm
{
    public String id();

    // called when charm is instantiated, useful e.g. to add attribute modifiers to the itemstack
    public ItemStack onCreation(ItemStack stack);

    public boolean subcribeToOnTick();

    // called every tick when the charm is equipped, useful for ticking charm effects
    // return true from subscribeToOnTick for this to be anbled
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level);

    public void tick(ServerPlayer player, ServerLevel level);
}
