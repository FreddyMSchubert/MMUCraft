package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
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

    public static CosmeticFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 1);
        return new CosmeticFakeItem(common.modelId(), common.title(), common.rarity(), common.tooltip());
    }
}
