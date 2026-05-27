package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

public final class EnchantmentLock
{
    private static final String LOCKED_TAG = "mainmod_enchantments_locked";
    private static final String LORE_TEXT = "Enchantments Locked";

    private EnchantmentLock() {
    }

    public static boolean isLocked(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean(LOCKED_TAG)
                .orElse(false);
    }

    public static void lock(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(LOCKED_TAG, true));
        addLore(stack);
    }

    public static void unlock(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(LOCKED_TAG);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        removeLore(stack);
    }

    public static ItemStack removeAllEnchantmentsAndUnlock(ItemStack stack) {
        clearEnchantments(stack, DataComponents.ENCHANTMENTS);
        clearEnchantments(stack, DataComponents.STORED_ENCHANTMENTS);
        unlock(stack);

        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemStack book = stack.transmuteCopy(Items.BOOK);
            clearEnchantments(book, DataComponents.ENCHANTMENTS);
            clearEnchantments(book, DataComponents.STORED_ENCHANTMENTS);
            return book;
        }

        return stack;
    }

    private static void clearEnchantments(ItemStack stack, DataComponentType<ItemEnchantments> componentType) {
        ItemEnchantments current = stack.getOrDefault(componentType, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.removeIf(enchantment -> true);
        stack.set(componentType, mutable.toImmutable());
    }

    private static void addLore(ItemStack stack) {
        List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (hasLore(lines)) {
            return;
        }

        lines.add(Component.literal(LORE_TEXT).withStyle(ChatFormatting.DARK_PURPLE));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void removeLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return;
        }

        List<Component> lines = new ArrayList<>(lore.lines());
        if (!lines.removeIf(line -> LORE_TEXT.equals(line.getString()))) {
            return;
        }

        if (lines.isEmpty()) {
            stack.remove(DataComponents.LORE);
        } else {
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
    }

    private static boolean hasLore(List<Component> lines) {
        return lines.stream().anyMatch(line -> LORE_TEXT.equals(line.getString()));
    }
}
