package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public class CosmeticFakeItem extends FakeItem
{
    public CosmeticFakeItem(String model_id, String title, Rarity rarity, String... tooltip)
    {
        super(Items.CARVED_PUMPKIN, model_id, title, rarity, 1, tooltip);
    }

    @Override
    public ItemStack createItemStack()
    {
        return super.createItemStack();
    }
}
