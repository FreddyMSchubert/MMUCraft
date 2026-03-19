package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public class BasicFakeItem extends FakeItem
{
    public BasicFakeItem(String model_id, String title, Rarity rarity, int maxStackSize, String... tooltip)
    {
        super(Items.COMMAND_BLOCK, model_id, title, rarity, maxStackSize, tooltip);
    }

    public static BasicFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 64);
        return new BasicFakeItem(common.modelId(), common.title(), common.rarity(), common.maxStackSize(), common.tooltip());
    }
}
