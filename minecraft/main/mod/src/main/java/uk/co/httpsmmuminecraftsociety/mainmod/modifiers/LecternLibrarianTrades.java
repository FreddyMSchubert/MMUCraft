package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettings;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;

import java.util.List;
import java.util.Optional;

public final class LecternLibrarianTrades {
    private LecternLibrarianTrades() {
    }

    public static void syncOffers(Villager villager, ServerLevel level) {
        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        offers.removeIf(LecternLibrarianTrades::isEnchantedBookOffer);

        getJobSiteEnchantedBook(villager, level)
                .flatMap(book -> createCopyOffer(book, level))
                .ifPresent(offer -> offers.add(0, offer));
    }

    private static boolean isEnchantedBookOffer(MerchantOffer offer) {
        return offer.getResult().is(Items.ENCHANTED_BOOK);
    }

    private static Optional<ItemStack> getJobSiteEnchantedBook(Villager villager, ServerLevel level) {
        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isEmpty() || jobSite.get().dimension() != level.dimension()) {
            return Optional.empty();
        }

        if (!(level.getBlockEntity(jobSite.get().pos()) instanceof LecternBlockEntity lectern)) {
            return Optional.empty();
        }

        ItemStack book = lectern.getBook();
        if (!book.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }

        return Optional.of(book.copyWithCount(1));
    }

    private static Optional<MerchantOffer> createCopyOffer(ItemStack sourceBook, ServerLevel level) {
        ItemEnchantments enchantments = sourceBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return Optional.empty();
        }

        TradeCosts costs = calculateCosts(enchantments);
        if (costs == null) {
            return Optional.empty();
        }

        ItemStack result = sourceBook.copyWithCount(1);
        if (enchantments.size() > 1)
            addRandomCurse(result, level);

        return Optional.of(new MerchantOffer(
                currencyCost(costs.emeraldCost()),
                Optional.of(itemCost(costs.dupeItem())),
                result,
                0,
                999999,
                0,
                0.0F
        ));
    }

    private static TradeCosts calculateCosts(ItemEnchantments enchantments) {
        int emeraldCost = 0;
        ItemStack dupeItem = ItemStack.EMPTY;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            Optional<EnchantmentSettings> settings = EnchantmentSettingsManager.getSettingsForEnch(enchantment);
            if (settings.isEmpty() || !settings.get().hasDupeItem()) {
                return null;
            }

            emeraldCost += enchantment.value().getAnvilCost() * level;
            ItemStack ingredient = settings.get().createDupeItemStack(level);

            if (dupeItem.isEmpty()) {
                dupeItem = ingredient;
            } else if (ItemStack.isSameItemSameComponents(dupeItem, ingredient)) {
                dupeItem.grow(ingredient.getCount());
            }
        }

        if (enchantments.size() > 1) {
            emeraldCost *= 2;
        }

        return new TradeCosts(Math.max(1, emeraldCost), dupeItem);
    }

    private static ItemCost currencyCost(int emeraldCost) {
        if (emeraldCost <= 64) {
            return new ItemCost(Items.EMERALD, emeraldCost);
        }

        return new ItemCost(Items.EMERALD_BLOCK, Math.max(1, Math.ceilDiv(emeraldCost, 9)));
    }

    private static ItemCost itemCost(ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            return new ItemCost(stack.getItem(), stack.getCount());
        }

        return new ItemCost(
                stack.typeHolder(),
                stack.getCount(),
                DataComponentExactPredicate.allOf(stack.getComponents()),
                stack
        );
    }

    private static void addRandomCurse(ItemStack book, ServerLevel level) {
        ItemEnchantments enchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        List<Holder.Reference<Enchantment>> curses = EnchantmentSettingsManager.curses.stream()
                .map(curse -> level.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(curse))
                .toList();

        if (curses.isEmpty()) {
            return;
        }

        RandomSource random = level.getRandom();
        mutable.set(curses.get(random.nextInt(curses.size())), 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
    }

    private record TradeCosts(int emeraldCost, ItemStack dupeItem) {}
}
