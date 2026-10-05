package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.ArrayList;
import java.util.List;

// manager for charm / armor interactions
public class CharmorManager
{
    public static final String TOOLTIP_INITIALLY_POPULATED_BOOL = "tooltip_initially_populated";
    public static final String ENDERITE_MARKER_BOOL = "is_enderite";
    public static final String CHARMS_HIDDEN_BOOL = "charms_hidden";
    public static final String ARMOR_MINIFIED_BOOL = "armor_minified";

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
        if (isEnderite(stack)) {
            armorTypeCharmSlots = 5;
        } else if (stack.is(ModItemTagProvider.SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS)) {
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
        if (!canMinifyArmor(stack)) return;
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)
                && !FeatureToggles.isEnabled(FeatureToggles.SOARING)) return;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL).orElse(false)) return;

        updateArmorTooltip(stack);

        CompoundTag updatedTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        updatedTag.putBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updatedTag));
    }
    public static ItemStack updateArmorTooltip(ItemStack stack) {
        if (!canMinifyArmor(stack)) return stack;

        boolean charmable = stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS);
        List<Component> tooltip = charmable
                ? new ArrayList<>()
                : new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (charmable) {
            List<StoredCharmData> storedCharms = CharmStackData.getStoredCharms(stack);
            tooltip.add(Component.literal("Charm Slots:").withStyle(style -> style.withItalic(false)));

            int slotCount = calcCharmSlotCount(stack);
            for (int i = 0; i < slotCount; i++) {
                String literal = (i + 1) + ": ";
                literal += i >= storedCharms.size() ? "-" : resolveStoredCharmDisplayName(storedCharms.get(i));
                tooltip.add(Component.literal(literal).withStyle(style -> style.withItalic(false)));
            }
        } else {
            tooltip.removeIf(line -> line.getString().equals("Armor: Full")
                    || line.getString().equals("Armor: Minified"));
        }

        if (FeatureToggles.isEnabled(FeatureToggles.SOARING)) {
            if (charmable) {
                tooltip.add(Component.literal("Charms: " + (charmsHidden(stack) ? "Hidden" : "Shown"))
                        .withStyle(style -> style.withItalic(false)));
            }
            tooltip.add(Component.literal("Armor: " + (armorMinified(stack) ? "Minified" : "Full"))
                    .withStyle(style -> style.withItalic(false)));
        }

        stack.set(DataComponents.LORE, new ItemLore(tooltip));
        refreshArmorCharmAppearance(stack);

        return stack;
    }
    public static void refreshArmorCharmAppearance(ItemStack stack) {
        if (!canMinifyArmor(stack)) return;

        List<StoredCharmData> storedCharms = getStoredArmorCharms(stack);
        Equippable itemDefaultEquippable = stack.getItem().components().get(DataComponents.EQUIPPABLE);
        if (itemDefaultEquippable == null) return;

        String materialString = getArmorMaterialType(stack);
        boolean appearanceEnabled = FeatureToggles.isEnabled(FeatureToggles.SOARING);
        boolean minified = appearanceEnabled && armorMinified(stack);

        if (storedCharms.isEmpty() || (appearanceEnabled && charmsHidden(stack))) {
            stack.set(DataComponents.EQUIPPABLE, minified && !materialString.isBlank()
                    ? EquippableCharmItemFeature.withAsset(itemDefaultEquippable, "armor_minified__" + materialString)
                    : isEnderite(stack)
                    ? EquippableCharmItemFeature.withAsset(itemDefaultEquippable, "enderite")
                    : itemDefaultEquippable);
            return;
        }

        StoredCharmData renderedCharm = storedCharms.getFirst();
        FakeItem renderedCharmItem = FakeItems.CHARM_ID_MAP.get(renderedCharm.charmId());
        if (renderedCharmItem == null) return;

        EquippableCharmItemFeature eqcif = renderedCharmItem.getFeature(EquippableCharmItemFeature.class);
        if (eqcif == null || eqcif.equippable().assetId().isEmpty()) return;

        if (materialString.isBlank()) return;

        String charmResourcePath = eqcif.equippable().assetId().get().identifier().getPath();
        int suffixIndex = charmResourcePath.indexOf("__charm");
        if (suffixIndex < 0) return;

        String withoutCharmSuffix = charmResourcePath.substring(0, suffixIndex);
        String newResourcePath = withoutCharmSuffix + "__" + materialString
                + (minified ? "__minified" : "");

        Equippable newEquippableSettings = EquippableCharmItemFeature.withAsset(
                itemDefaultEquippable,
                newResourcePath
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

    public static boolean isEnderite(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(ENDERITE_MARKER_BOOL).orElse(false);
    }


    public static boolean charmsHidden(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(CHARMS_HIDDEN_BOOL).orElse(false);
    }

    public static boolean armorMinified(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(ARMOR_MINIFIED_BOOL).orElse(false);
    }

    public static boolean canMinifyArmor(ItemStack stack) {
        return stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)
                || stack.is(ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS);
    }

    private static String getArmorMaterialType(ItemStack stack) {
        if (isEnderite(stack)) return "enderite";
        if (stack.is(Items.DIAMOND_HELMET)) return "diamond";
        if (stack.is(Items.NETHERITE_HELMET)) return "netherite";
        if (stack.is(Items.IRON_HELMET)) return "iron";
        if (stack.is(Items.GOLDEN_HELMET)) return "gold";
        if (stack.is(Items.COPPER_HELMET)) return "copper";
        if (stack.is(Items.LEATHER_HELMET)) return "leather";
        if (stack.is(Items.CHAINMAIL_HELMET)) return "chainmail";
        if (stack.is(Items.TURTLE_HELMET)) return "turtle_scute";
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
