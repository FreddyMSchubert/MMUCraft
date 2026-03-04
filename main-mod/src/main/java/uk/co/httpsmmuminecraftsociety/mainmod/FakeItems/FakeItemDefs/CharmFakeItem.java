package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;

public class CharmFakeItem extends FakeItem
{
    private final Charm charm;

    public CharmFakeItem(String title, Rarity rarity, Charm charm, String... tooltip)
    {
        super(Items.COMMAND_BLOCK, charm.id(), title, rarity, 1, tooltip);

        this.charm = charm;
    }

    public Charm getCharm() {
        return charm;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();

        stack = charm.onCreation(stack);

        return stack;
    }
}
