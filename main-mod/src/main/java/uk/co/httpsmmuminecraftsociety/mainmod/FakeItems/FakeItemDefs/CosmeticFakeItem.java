package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class CosmeticFakeItem extends FakeItem
{
    public CosmeticFakeItem(String model_id, String title, Rarity rarity, String... tooltip)
    {
        super(Items.CARVED_PUMPKIN, model_id, title, rarity, 1, tooltip);
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.enchant();

        MinecraftServer server = new Object;
        server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
    }
}
