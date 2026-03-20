package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

public record DyeableItemFeature(
        int dyeColor
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        String tintColorHex = json.get("tintColor").getAsString();
        return new DyeableItemFeature(JsonUtils.parseTintColor(tintColorHex));
    }

    @Override
    public void apply(ItemStack stack)
    {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
        stack.set(DataComponents.LORE, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(Component.literal("Dyeable")));
    }
}
