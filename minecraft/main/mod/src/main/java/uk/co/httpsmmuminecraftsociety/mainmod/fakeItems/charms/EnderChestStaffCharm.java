package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

public class EnderChestStaffCharm implements Charm, UseCallbackCharm
{
    @Override
    public ItemStack onUse(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        PlayerEnderChestContainer playerEnderChestContainer = player.getEnderChestInventory();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, playerEnderChestContainer), Component.literal("Soulbound Storage Staff")));
        return stack;
    }
}
