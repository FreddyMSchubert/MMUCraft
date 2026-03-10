package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class SoulboundEnchantment {
    public static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive)
    {
        if (alive) {
            return;
        }

        Holder<Enchantment> soulbound = newPlayer.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SOULBOUND);

        Inventory oldInv = oldPlayer.getInventory();
        Inventory newInv = newPlayer.getInventory();

        for (int slot = 0; slot < oldInv.getContainerSize(); slot++) {
            ItemStack stack = oldInv.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack) > 0) {
                newInv.setItem(slot, stack.copy());
            }
        }

        newInv.setChanged();
    }
}