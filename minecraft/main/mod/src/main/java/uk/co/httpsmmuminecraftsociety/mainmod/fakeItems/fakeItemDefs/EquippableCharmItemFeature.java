package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

public record EquippableCharmItemFeature(
        Equippable equippable
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        String rawSlot = json.get("equipmentSlot").getAsString();
        EquipmentSlot slot = JsonUtils.parseEquipmentSlot(rawSlot);

        String equippableAssetId = json.get("equippableAssetId").getAsString();

        return new EquippableCharmItemFeature(createEquippableSettings(equippableAssetId, slot));
    }

    public static Equippable createEquippableSettings(
            String assetId,
            EquipmentSlot slot
    )
    {
        return Equippable.builder(slot)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, assetId)))
                .setSwappable(false)
                .setDispensable(false)
                .setDamageOnHurt(false)
                .build();
    }

    @Override
    public void apply(ItemStack stack)
    {
        stack.set(DataComponents.EQUIPPABLE, equippable);
    }
}
