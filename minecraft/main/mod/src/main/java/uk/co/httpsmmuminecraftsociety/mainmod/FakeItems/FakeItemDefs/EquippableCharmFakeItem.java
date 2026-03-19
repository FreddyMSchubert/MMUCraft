package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
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
import uk.co.httpsmmuminecraftsociety.mainmod.itemdata.CharmCodeRegistry;

public class EquippableCharmFakeItem extends CharmFakeItem
{
    private final Equippable equippableSettings;

    public EquippableCharmFakeItem(
            int effectId,
            String model_id,
            String title,
            Rarity rarity,
            String equippable_asset_id,
            EquipmentSlot slot,
            boolean swappable,
            boolean dispensable,
            boolean damageOnHurt,
            Charm charm,
            String... tooltip
    )
    {
        super(effectId, model_id, title, rarity, charm, tooltip);

        this.equippableSettings = createEquippableSettings(equippable_asset_id, slot, swappable, dispensable, damageOnHurt);
    }

    public Equippable getEquippableSettings() {
        return equippableSettings;
    }

    public static Equippable createEquippableSettings(
            String assetId,
            EquipmentSlot slot,
            boolean swappable,
            boolean dispensable,
            boolean damageOnHurt
    )
    {
        return Equippable.builder(slot)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, assetId)))
                .setSwappable(swappable)
                .setDispensable(dispensable)
                .setDamageOnHurt(damageOnHurt)
                .build();
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.set(DataComponents.EQUIPPABLE, equippableSettings);
        return stack;
    }

    public static EquippableCharmFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 1);
        JsonObject looks = getLooksObject(root);
        JsonObject behaviour = getBehaviourObject(root);
        JsonObject equippable = getEquippableObject(root);

        int effectId = requiredInt(behaviour, root, "effectId", sourcePath);
        Charm charm = CharmCodeRegistry.getRequired(effectId, sourcePath);

        String assetId = requiredString(looks, root, "equippable_asset_id", sourcePath);

        String slotString = optionalString(equippable, behaviour, "equipmentSlot", null);
        if (slotString == null) {
            boolean isLeggings = optionalBoolean(looks, root, "isLeggings", false);
            slotString = isLeggings ? "legs" : "chest";
        }

        boolean swappable = optionalBoolean(equippable, behaviour, "swappable", false);
        boolean dispensable = optionalBoolean(equippable, behaviour, "dispensable", false);
        boolean damageOnHurt = optionalBoolean(equippable, behaviour, "damageOnHurt", false);

        return new EquippableCharmFakeItem(
                effectId,
                common.modelId(),
                common.title(),
                common.rarity(),
                assetId,
                parseEquipmentSlot(slotString, sourcePath),
                swappable,
                dispensable,
                damageOnHurt,
                charm,
                common.tooltip()
        );
    }
}
