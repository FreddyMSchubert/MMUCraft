package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CurseUtils {
    public static Optional<Holder<Enchantment>> getRandomValidCurse(ItemStack stack, ServerLevel level, String seed) {
        return getRandomValidCurse(stack, level.registryAccess(), seed);
    }

    public static Optional<Holder<Enchantment>> getRandomValidCurse(ItemStack stack, HolderLookup.Provider registries, String seed) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        RandomSource random = Utils.randomFromString(seed);

        return registries.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.CURSE)
                .flatMap(curseSet -> selectValidCurse(
                        stack,
                        enchantments,
                        curseSet.stream()
                                .filter(curse -> isValidCurse(stack, enchantments, curse))
                                .sorted(Comparator.comparing(Holder::getRegisteredName))
                                .toList(),
                        random
                ));
    }

    private static Optional<Holder<Enchantment>> selectValidCurse(ItemStack stack, ItemEnchantments enchantments, List<Holder<Enchantment>> validCurses, RandomSource random) {
        if (validCurses.isEmpty()) {
            return Optional.empty();
        }

        if (stack.is(Items.ENCHANTED_BOOK)) {
            List<Holder<Enchantment>> bookEnchantments = enchantments.keySet().stream()
                    .filter(enchantment -> !enchantment.is(EnchantmentTags.CURSE))
                    .toList();

            if (!bookEnchantments.isEmpty()) {
                Set<Holder<Item>> bookTargetItems = bookEnchantments.stream()
                        .flatMap(enchantment -> enchantment.value().getSupportedItems().stream())
                        .collect(Collectors.toSet());

                List<Holder<Enchantment>> matchingCurses = validCurses.stream()
                        .filter(curse -> targetsAnyBookItem(bookTargetItems, curse))
                        .toList();
                if (!matchingCurses.isEmpty()) {
                    return Optional.of(matchingCurses.get(random.nextInt(matchingCurses.size())));
                }

                return Optional.empty();
            }
        }

        return Optional.of(validCurses.get(random.nextInt(validCurses.size())));
    }

    private static boolean isValidCurse(ItemStack stack, ItemEnchantments enchantments, Holder<Enchantment> curse) {
        if (enchantments.getLevel(curse) > 0) {
            return false;
        }

        if (!stack.is(Items.BOOK) && !stack.is(Items.ENCHANTED_BOOK) && !curse.value().isSupportedItem(stack)) {
            return false;
        }

        return EnchantmentHelper.isEnchantmentCompatible(enchantments.keySet(), curse);
    }

    private static boolean targetsAnyBookItem(Set<Holder<Item>> bookTargetItems, Holder<Enchantment> curse) {
        return curse.value().getSupportedItems().stream()
                .anyMatch(bookTargetItems::contains);
    }
}
