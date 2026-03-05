package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class EquippableCharmFakeItem extends CharmFakeItem
{
    private final Equippable equippableSettings;

    public EquippableCharmFakeItem(int effectId, String title, Rarity rarity, String equippable_asset_id, EquipmentSlot slot, Charm charm, String... tooltip)
    {
        super(effectId, title, rarity, charm, tooltip);

        this.equippableSettings = Equippable.builder(slot).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, equippable_asset_id))).setSwappable(false).setDispensable(false).setDamageOnHurt(false).build();
    }

    public Equippable getEquippableSettings() {
        return equippableSettings;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.set(DataComponents.EQUIPPABLE, equippableSettings);
        return stack;
    }
}
