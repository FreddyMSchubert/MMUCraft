package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.Optional;

public class Utils
{
    // Player attribute modifiers

    public static void applyPlayerModifier(ServerPlayer player,
                                           net.minecraft.core.Holder<Attribute> attr,
                                           Identifier id,
                                           double amount,
                                           AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        AttributeModifier existing = inst.getModifier(id);
        if (existing != null) inst.removeModifier(existing);

        inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    public static void removePlayerModifier(ServerPlayer player,
                                            net.minecraft.core.Holder<Attribute> attr,
                                            Identifier id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        AttributeModifier existing = inst.getModifier(id);
        if (existing != null) inst.removeModifier(existing);
    }

    // Item attribute modifiers

    public static ItemStack applyItemAttrModifier(ItemStack stack, String id, Holder<Attribute> attributeType, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slotgroup) {
        Identifier attrId = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, id);

        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        AttributeModifier newMod = new AttributeModifier(attrId, amount, operation);
        ItemAttributeModifiers newMods = mods.withModifierAdded(attributeType, newMod, slotgroup);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, newMods);

        return stack;
    }

    public static ItemStack removeItemAttrModifier(ItemStack stack, String id, Holder<Attribute> attributeType) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Identifier toRemoveId = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, id);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry mod : mods.modifiers()) {
            if (mod.matches(attributeType, toRemoveId)) continue; // skip the attr to remove, then reassemble
            builder.add(mod.attribute(), mod.modifier(), mod.slot());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        return stack;
    }

    // Curse selection

    public static Optional<Holder<Enchantment>> getRandomValidCurse(ItemStack stack, ServerLevel level) {
        return getRandomValidCurse(stack, level.registryAccess(), level.getRandom());
    }

    public static Optional<Holder<Enchantment>> getRandomValidCurse(ItemStack stack, HolderLookup.Provider registries, RandomSource random) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.CURSE)
                .map(curseSet -> curseSet.stream()
                        .filter(curse -> isValidCurse(stack, curse))
                        .toList())
                .filter(validCurses -> !validCurses.isEmpty())
                .map(validCurses -> validCurses.get(random.nextInt(validCurses.size())));
    }

    private static boolean isValidCurse(ItemStack stack, Holder<Enchantment> curse) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);

        if (enchantments.getLevel(curse) > 0) {
            return false;
        }

        if (!stack.is(Items.BOOK) && !stack.is(Items.ENCHANTED_BOOK) && !curse.value().isSupportedItem(stack)) {
            return false;
        }

        return EnchantmentHelper.isEnchantmentCompatible(enchantments.keySet(), curse);
    }

    // Color conversion

    public static int rgbToMinecraftColor(int r, int g, int b) {
        return ARGB.color(0, r, g, b);
    }
}
