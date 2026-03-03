package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class CharmFakeItem extends FakeItem
{
    private final Charm charm;

    public CharmFakeItem(Item baseItem, String title, Rarity rarity, Charm charm, String... tooltip)
    {
        super(baseItem, charm.id(), title, rarity, 1, tooltip);

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

        if (charm.subcribeToOnTick())
        {
            CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = cd.copyTag();
            tag.putBoolean(Utils.TAG_TICK, true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        return stack;
    }
}
