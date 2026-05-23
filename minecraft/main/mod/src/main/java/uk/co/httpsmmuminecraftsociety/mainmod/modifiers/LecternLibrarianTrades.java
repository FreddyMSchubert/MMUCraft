package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import java.util.Optional;

public final class LecternLibrarianTrades {
    private static final int MAX_USES = 999999;

    private LecternLibrarianTrades() {
    }

    public static void syncOffers(Villager villager, ServerLevel level) {
        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        offers.removeIf(LecternLibrarianTrades::isEnchantedBookOffer);

        getJobSiteEnchantedBook(villager, level)
                .map(LecternLibrarianTrades::createCopyOffer)
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

    private static MerchantOffer createCopyOffer(ItemStack book) {
        return new MerchantOffer(
                new ItemCost(Items.BOOK),
                Optional.of(new ItemCost(Items.EMERALD_BLOCK)),
                book,
                0,
                MAX_USES,
                0,
                0.0F
        );
    }
}
