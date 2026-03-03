package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public class BasicFakeItem extends FakeItem
{
    public BasicFakeItem(String model_id, String title, Rarity rarity, int maxStackSize, String... tooltip)
    {
        super(Items.COMMAND_BLOCK, model_id, title, rarity, maxStackSize, tooltip);
    }
}
