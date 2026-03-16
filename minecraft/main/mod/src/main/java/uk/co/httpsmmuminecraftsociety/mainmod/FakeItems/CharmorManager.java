package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CharmFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;

import java.util.ArrayList;
import java.util.List;

// manager for charm / armor interactions
public class CharmorManager
{
    // the following custom data compound tags are present in every armor, defining charms equipped
    public static final String TOOLTIP_INITIALLY_POPULATED_BOOL = "tooltip_initially_populated";

    public static int calcCharmSlotCount(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return 0;

        int charmBoostLevel = 0;
        for (var entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(ModEnchantments.CHARM_BOOST)) {
                charmBoostLevel = entry.getIntValue();
                break;
            }
        }

        int armorTypeCharmSlots = 0;
        if (stack.is(ModItemTagProvider.SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS)) armorTypeCharmSlots = 1;
        else if (stack.is(ModItemTagProvider.DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS)) armorTypeCharmSlots = 2;
        else if (stack.is(ModItemTagProvider.TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS)) armorTypeCharmSlots = 3;

        return Math.min(charmBoostLevel + 1, armorTypeCharmSlots);
    }
    public static int calcUsedCharmSlotCount(ItemStack stack) {
        initArmorTooltipIfUninitialized(stack);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int[] charmSlots = tag.getIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID).get();
        return charmSlots.length;
    }
    public static boolean canEquipMoreCharms(ItemStack stack) {
        return calcUsedCharmSlotCount(stack) < calcCharmSlotCount(stack);
    }

    public static ItemStack initArmorTooltipIfUninitialized(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return stack;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TOOLTIP_INITIALLY_POPULATED_BOOL)) return stack;

        tag.putIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID, new int[0]);
        tag.putBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL, true);

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return stack;
    }

    public static ItemStack updateArmorTooltip(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return stack;
        stack = initArmorTooltipIfUninitialized(stack);

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("Charm Slots:"));

        List<CharmFakeItem> charmFakeItems = CharmsManager.getAbilitiesFromItemStack(stack);
        for (int i = 0; i < calcCharmSlotCount(stack); i++) {
            String literal = "[Slot " + (i+1) + "]: ";
            if (i >= charmFakeItems.size()) {
                literal += "-";
            } else {
                literal += charmFakeItems.get(i).getTitle().getString();
            }
            tooltip.add(Component.literal(literal));
        }

        stack.set(DataComponents.LORE, new ItemLore(tooltip));

        //CompoundTag equippedCharmsTag = tag.getCompoundOrEmpty(EQUIPPED_CHARMS_ID);
        return stack;
    }
}
