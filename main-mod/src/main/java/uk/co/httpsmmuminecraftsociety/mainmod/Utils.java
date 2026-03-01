package uk.co.httpsmmuminecraftsociety.mainmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public class Utils
{
    public static ItemStack createCustomModelDataItemStack(Item item, String customModelData)
    {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(customModelData), List.of()));
        return stack;
    }
}
