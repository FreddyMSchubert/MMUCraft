package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
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

import java.util.Optional;

public final class LecternLibrarianTrades {
    private static final int LECTERN_COPY_MAX_USES = 999_999;
    private static final int LECTERN_COPY_XP = 10;

    private LecternLibrarianTrades() {
    }

    public static void syncOffers(Villager villager, ServerLevel level) {
        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        Optional<MerchantOffer> copyOffer = getJobSiteEnchantedBook(villager, level)
                .flatMap(LecternLibrarianTrades::createCopyOffer);

        if (copyOffer.isPresent() && offers.stream()
                .filter(LecternLibrarianTrades::isLecternCopyOffer)
                .anyMatch(existing -> sameTrade(existing, copyOffer.get()))) {
            return;
        }

        offers.removeIf(LecternLibrarianTrades::isLecternCopyOffer);
        copyOffer.ifPresent(offer -> offers.add(0, offer));
    }

    private static boolean sameTrade(MerchantOffer first, MerchantOffer second) {
        return first.getItemCostA().equals(second.getItemCostA())
                && first.getItemCostB().equals(second.getItemCostB())
                && ItemStack.matches(first.getResult(), second.getResult());
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

    private static Optional<MerchantOffer> createCopyOffer(ItemStack sourceBook) {
        ItemEnchantments enchantments = sourceBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return Optional.empty();
        }

        int emeraldCost = calculateEmeraldCost(enchantments);

        ItemStack result = sourceBook.copyWithCount(1);

        return Optional.of(new MerchantOffer(
                currencyCost(emeraldCost),
                Optional.empty(),
                result,
                0,
                LECTERN_COPY_MAX_USES,
                LECTERN_COPY_XP,
                0.0F
        ));
    }

    private static int calculateEmeraldCost(ItemEnchantments enchantments) {
        int emeraldCost = 0;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            emeraldCost += entry.getKey().value().getAnvilCost() * entry.getIntValue();
        }

        return Math.max(1, emeraldCost);
    }

    private static ItemCost currencyCost(int emeraldCost) {
        if (emeraldCost <= 64) {
            return new ItemCost(Items.EMERALD, emeraldCost);
        }

        return new ItemCost(Items.EMERALD_BLOCK, Math.max(1, Math.ceilDiv(emeraldCost, 9)));
    }

}
