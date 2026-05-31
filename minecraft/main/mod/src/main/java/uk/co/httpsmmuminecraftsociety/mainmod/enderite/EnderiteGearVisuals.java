package uk.co.httpsmmuminecraftsociety.mainmod.enderite;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;

import java.util.Map;

public final class EnderiteGearVisuals {
    private static final String EQUIPMENT_ASSET_ID = "enderite";
    private static final Map<Item, String> ITEM_MODEL_PATHS = Map.of(
            Items.NETHERITE_HELMET, "enderite/helmet",
            Items.NETHERITE_CHESTPLATE, "enderite/chestplate",
            Items.NETHERITE_LEGGINGS, "enderite/leggings",
            Items.NETHERITE_BOOTS, "enderite/boots",
            Items.NETHERITE_SWORD, "enderite/sword",
            Items.NETHERITE_PICKAXE, "enderite/pickaxe",
            Items.NETHERITE_AXE, "enderite/axe",
            Items.NETHERITE_SHOVEL, "enderite/shovel",
            Items.NETHERITE_HOE, "enderite/hoe"
    );

    private EnderiteGearVisuals() {
    }

    public static boolean supports(ItemStack stack) {
        return ITEM_MODEL_PATHS.containsKey(stack.getItem());
    }

    public static void apply(ItemStack stack) {
        applyItemModel(stack);
        applyBaseArmorAppearance(stack);
    }

    public static void applyBaseArmorAppearance(ItemStack stack) {
        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) {
            return;
        }

        stack.set(
                DataComponents.EQUIPPABLE,
                EquippableCharmItemFeature.createEquippableSettings(EQUIPMENT_ASSET_ID, slot)
        );
    }

    private static void applyItemModel(ItemStack stack) {
        String modelPath = ITEM_MODEL_PATHS.get(stack.getItem());
        if (modelPath == null) {
            return;
        }

        stack.set(
                DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, modelPath)
        );
    }

    private static EquipmentSlot getArmorSlot(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.NETHERITE_HELMET) return EquipmentSlot.HEAD;
        if (item == Items.NETHERITE_CHESTPLATE) return EquipmentSlot.CHEST;
        if (item == Items.NETHERITE_LEGGINGS) return EquipmentSlot.LEGS;
        if (item == Items.NETHERITE_BOOTS) return EquipmentSlot.FEET;
        return null;
    }
}
