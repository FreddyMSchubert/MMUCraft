package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
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
import uk.co.httpsmmuminecraftsociety.mainmod.utils.CurseUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LecternLibrarianTrades {
    private static final int LECTERN_COPY_MAX_USES = 999_999;

    private LecternLibrarianTrades() {
    }

    public static void syncOffers(Villager villager, ServerLevel level) {
        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        offers.removeIf(LecternLibrarianTrades::isLecternCopyOffer);

        getJobSiteEnchantedBook(villager, level)
                .flatMap(book -> createCopyOffer(book, level, villager.getUUID().toString()))
                .ifPresent(offer -> offers.add(0, offer));
    }

    private static boolean isLecternCopyOffer(MerchantOffer offer) {
        return offer.getResult().is(Items.ENCHANTED_BOOK)
                && offer.getMaxUses() == LECTERN_COPY_MAX_USES;
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

    private static Optional<MerchantOffer> createCopyOffer(ItemStack sourceBook, ServerLevel level, String villagerSeed) {
        ItemEnchantments enchantments = sourceBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return Optional.empty();
        }

        long nonCurseEnchantmentCount = enchantments.keySet().stream()
                .filter(enchantment -> !enchantment.is(EnchantmentTags.CURSE))
                .count();
        boolean hasCurse = enchantments.keySet().stream()
                .anyMatch(enchantment -> enchantment.is(EnchantmentTags.CURSE));

        TradeCosts costs = calculateCosts(enchantments, nonCurseEnchantmentCount, villagerSeed + ":dupe_item");

        ItemStack result = sourceBook.copyWithCount(1);
        if (nonCurseEnchantmentCount > 1 && !hasCurse) {
            addRandomCurse(result, level, villagerSeed + ":curse");
        }

        return Optional.of(new MerchantOffer(
                currencyCost(costs.emeraldCost()),
                costs.dupeItem().isEmpty()
                        ? Optional.empty()
                        : Optional.of(itemCost(costs.dupeItem())),
                result,
                0,
                LECTERN_COPY_MAX_USES,
                0,
                0.0F
        ));
    }

    private static TradeCosts calculateCosts(ItemEnchantments enchantments, long nonCurseEnchantmentCount, String dupeItemSeed) {
        int emeraldCost = 0;
        int totalEnchantmentLevels = 0;
        List<DupeItemCandidate> dupeItemCandidates = new ArrayList<>();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            Optional<EnchantmentSettings> settings = EnchantmentSettingsManager.getSettingsForEnch(enchantment);
            emeraldCost += enchantment.value().getAnvilCost() * level;
            totalEnchantmentLevels += level;

            if (settings.isEmpty() || !settings.get().hasDupeItem()) {
                continue;
            }

            ItemStack ingredient = settings.get().createDupeItemStack(level);
            int matchingIndex = -1;
            for (int i = 0; i < dupeItemCandidates.size(); i++) {
                if (ItemStack.isSameItemSameComponents(dupeItemCandidates.get(i).stack(), ingredient)) {
                    matchingIndex = i;
                    break;
                }
            }

            if (matchingIndex == -1) {
                dupeItemCandidates.add(new DupeItemCandidate(ingredient, level));
            } else {
                DupeItemCandidate candidate = dupeItemCandidates.get(matchingIndex);
                candidate.stack().grow(ingredient.getCount());
                dupeItemCandidates.set(matchingIndex, new DupeItemCandidate(candidate.stack(), candidate.enchantmentLevels() + level));
            }
        }

        if (nonCurseEnchantmentCount > 1) {
            emeraldCost *= 2;
        }

        ItemStack dupeItem = ItemStack.EMPTY;
        if (!dupeItemCandidates.isEmpty()) {
            dupeItemCandidates.sort(Comparator.comparing(candidate -> candidate.stack().typeHolder().getRegisteredName() + candidate.stack().getComponentsPatch()));
            RandomSource random = Utils.randomFromString(dupeItemSeed);
            DupeItemCandidate candidate = dupeItemCandidates.get(random.nextInt(dupeItemCandidates.size()));
            dupeItem = candidate.stack().copy();
            int count = candidate.stack().getCount() + totalEnchantmentLevels - candidate.enchantmentLevels();
            dupeItem.setCount(Math.min(count, dupeItem.getMaxStackSize()));
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

    private static void addRandomCurse(ItemStack book, ServerLevel level, String curseSeed) {
        ItemEnchantments enchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        CurseUtils.getRandomValidCurse(book, level, curseSeed).ifPresent(curse -> {
            mutable.set(curse, 1);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        });
    }

    private record TradeCosts(int emeraldCost, ItemStack dupeItem) {}
    private record DupeItemCandidate(ItemStack stack, int enchantmentLevels) {}
}
