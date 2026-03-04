package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.UseCallbackCharm;

import java.awt.*;

public class CraftingStaffCharm implements Charm, UseCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-crafting-staff";
    }

    @Override
    public @NotNull ItemStack onCreation(ItemStack stack)
    {
        return stack;
    }

    @Override
    public ItemStack onUse(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv), Component.literal("Crafting Staff")));
        return stack;
    }
}
