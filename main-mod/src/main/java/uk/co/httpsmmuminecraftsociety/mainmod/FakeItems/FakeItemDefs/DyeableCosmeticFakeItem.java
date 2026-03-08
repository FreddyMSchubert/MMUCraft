package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;

public class DyeableCosmeticFakeItem extends CosmeticFakeItem
{
    private final int dyeColor;

    public DyeableCosmeticFakeItem(String model_id, String title, Rarity rarity, int dyeColor, String... tooltip)
    {
        super(model_id, title, rarity, tooltip);

        this.dyeColor = dyeColor;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
        stack.set(DataComponents.LORE, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(Component.literal("Dyeable")));
        return stack;
    }
}
