package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;

// manager for charm / armor interactions
public class CharmorManager
{
    public static final String TOOLTIP_INITIALLY_POPULATED_BOOL = "tooltip_initially_populated";

    public static int calcCharmSlotCount(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return 0;

        boolean hasCharmBoost = false;
        for (var entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(ModEnchantments.CHARM_BOOST)) {
                hasCharmBoost = entry.getIntValue() > 0;
                break;
            }
        }

        int armorTypeCharmSlots = 0;
        if (stack.is(ModItemTagProvider.SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS)) {
            armorTypeCharmSlots = 1;
        } else if (stack.is(ModItemTagProvider.DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS)) {
            armorTypeCharmSlots = 2;
        } else if (stack.is(ModItemTagProvider.TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS)) {
            armorTypeCharmSlots = 3;
        }

        return 1 + (hasCharmBoost ? armorTypeCharmSlots : 0);
    }
    public static boolean canEquipMoreCharms(ItemStack stack) {
        return getStoredArmorCharms(stack).size() < calcCharmSlotCount(stack);
    }
    public static List<StoredCharmData> getStoredArmorCharms(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return List.of();
        return CharmStackData.getStoredCharms(stack);
    }

    public static void initArmorTooltipIfUninitialized(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL).orElse(false)) return;

        updateArmorTooltip(stack);

        CompoundTag updatedTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        updatedTag.putBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updatedTag));
    }
    public static ItemStack updateArmorTooltip(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            return stack;
        }

        List<StoredCharmData> storedCharms = CharmStackData.getStoredCharms(stack);

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("Charm Slots:"));

        int slotCount = calcCharmSlotCount(stack);
        for (int i = 0; i < slotCount; i++) {
            String literal = "[Slot " + (i + 1) + "]: ";

            if (i >= storedCharms.size()) {
                literal += "-";
            } else {
                literal += resolveStoredCharmDisplayName(storedCharms.get(i));
            }

            tooltip.add(Component.literal(literal));
        }

        stack.set(DataComponents.LORE, new ItemLore(tooltip));
        refreshArmorCharmAppearance(stack);

        return stack;
    }
    public static void refreshArmorCharmAppearance(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return;

        List<StoredCharmData> storedCharms = CharmStackData.getStoredCharms(stack);
        if (storedCharms.isEmpty()) return;

        StoredCharmData renderedCharm = storedCharms.getFirst();
        FakeItem renderedCharmItem = FakeItems.CHARM_ID_MAP.get(renderedCharm.charmId());
        if (renderedCharmItem == null) return;

        EquippableCharmItemFeature eqcif = renderedCharmItem.getFeature(EquippableCharmItemFeature.class);
        if (eqcif == null || eqcif.equippable().assetId().isEmpty()) return;

        String materialString = getArmorMaterialType(stack);
        if (materialString.isBlank()) return;

        String charmResourcePath = eqcif.equippable().assetId().get().identifier().getPath();
        int suffixIndex = charmResourcePath.indexOf("__charm");
        if (suffixIndex < 0) return;

        String withoutCharmSuffix = charmResourcePath.substring(0, suffixIndex);
        String newResourcePath = withoutCharmSuffix + "__" + materialString;

        Equippable newEquippableSettings = EquippableCharmItemFeature.createEquippableSettings(
                newResourcePath,
                eqcif.equippable().slot()
        );

        stack.set(DataComponents.EQUIPPABLE, newEquippableSettings);
    }

    private static String resolveStoredCharmDisplayName(StoredCharmData storedCharm) {
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
        if (fakeItem == null) {
            return "[Unknown charm " + storedCharm.charmId() + "]";
        }

        CharmItemFeature charmFeature = fakeItem.getFeature(CharmItemFeature.class);
        if (charmFeature == null) {
            return fakeItem.title();
        }

        return charmFeature.getDisplayTitle(storedCharm.level());
    }

    private static String getArmorMaterialType(ItemStack stack) {
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND)) return "diamond";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE)) return "netherite";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_IRON)) return "iron";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_GOLD)) return "gold";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_COPPER)) return "copper";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_LEATHER)) return "leather";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_CHAINMAIL)) return "chainmail";
        return "";
    }
}
