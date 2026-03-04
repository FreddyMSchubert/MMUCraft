package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.ArrayList;
import java.util.List;

// manager for charm / armor interactions
public class CharmorManager
{
    // the following custom data compound tags are present in every armor, defining charms equipped
    public static final String TOOLTIP_INITIALLY_POPULATED_BOOL = "tooltip_initially_populated";
    public static String EQUIPPED_CHARM_BASE = "equipped_charm_"; // max 3 charms, indexed 0-2, therefore: equipped_charm_0, equipped_charm_1, equipped_charm_2

    public static final ResourceKey<Enchantment> CHARM_BOOST_KEY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_boost"));

    public static int calcCharmSlotCount(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return 0;

        int charmBoostLevel = 0;
        for (var entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(CHARM_BOOST_KEY)) {
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

    public static ItemStack initArmorTooltipIfUninitialized(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return stack;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TOOLTIP_INITIALLY_POPULATED_BOOL)) return stack;

        for (int i = 0; i < 3; i++) {
            tag.putString(EQUIPPED_CHARM_BASE + i, "");
        }
        tag.putBoolean(TOOLTIP_INITIALLY_POPULATED_BOOL, true);

        CustomData updatedCmd = CustomData.of(tag);
        stack.set(DataComponents.CUSTOM_DATA, updatedCmd);

        MainMod.LOGGER.info("Populated tooltip for armor item " + stack.getHoverName().getString() + " (initialization)");

        return stack;
    }

    public static ItemStack updateArmorTooltip(ItemStack stack) {
        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return stack;
        stack = initArmorTooltipIfUninitialized(stack);

        CustomData cmd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cmd.copyTag();

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("Charm Slots:"));

        for (int i = 0; i < calcCharmSlotCount(stack); i++) {
            String charmId = tag.getStringOr(EQUIPPED_CHARM_BASE + i, "");
            String literal = "[Slot " + (i+1) + "]: ";
            if (charmId.isEmpty()) {
                literal += "Empty";
            } else {
                FakeItem charm = FakeItems.ID_MAP.get(charmId);
                if (charm == null) literal += "Unknown Charm (ID: " + charmId + "). Please report this to a mod.";
                literal += charm.getTitle().getString();
            }
            tooltip.add(Component.literal(literal));
        }

        stack.set(DataComponents.LORE, new ItemLore(tooltip));

        //CompoundTag equippedCharmsTag = tag.getCompoundOrEmpty(EQUIPPED_CHARMS_ID);
        return stack;
    }
}
