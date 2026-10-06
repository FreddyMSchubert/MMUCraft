package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public final class FishMeatFishermanTrades {
    private static final int FISH_MEAT_COST = 6;
    private static final int MAX_USES = 16;
    private static final int XP = 2;
    private static final float PRICE_MULTIPLIER = 0.05F;
    private static final String COOKED_RED_FISH_ID = "cooked-red-fish";
    private static final String COOKED_WHITE_FISH_ID = "cooked-white-fish";

    private FishMeatFishermanTrades() {
    }

    public static void appendOffers(Villager villager) {
        if (!villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        syncOffer(offers, COOKED_RED_FISH_ID);
        syncOffer(offers, COOKED_WHITE_FISH_ID);
    }

    static void syncOffer(MerchantOffers offers, String fakeItemId) {
        ItemStack fishMeat = FakeItems.createFakeItemStack(fakeItemId, FISH_MEAT_COST);
        ItemCost fishMeatCost = new ItemCost(
                fishMeat.typeHolder(),
                FISH_MEAT_COST,
                DataComponentExactPredicate.someOf(fishMeat.getComponents(), DataComponents.CUSTOM_MODEL_DATA, DataComponents.CUSTOM_NAME),
                fishMeat
        );

        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (!FakeItems.isSpecificFakeItem(offer.getBaseCostA(), fakeItemId)) {
                continue;
            }
            if (!offer.getItemCostA().components().equals(fishMeatCost.components())) {
                MerchantOffer updated = new MerchantOffer(
                        fishMeatCost,
                        offer.getItemCostB(),
                        offer.getResult(),
                        offer.getUses(),
                        offer.getMaxUses(),
                        offer.getXp(),
                        offer.getPriceMultiplier(),
                        offer.getDemand()
                );
                updated.setSpecialPriceDiff(offer.getSpecialPriceDiff());
                offers.set(i, updated);
            }
            return;
        }

        offers.add(new MerchantOffer(
                fishMeatCost,
                new ItemStack(Items.EMERALD),
                MAX_USES,
                XP,
                PRICE_MULTIPLIER
        ));
    }
}
