package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
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
        appendOfferIfMissing(offers, COOKED_RED_FISH_ID);
        appendOfferIfMissing(offers, COOKED_WHITE_FISH_ID);
    }

    private static void appendOfferIfMissing(MerchantOffers offers, String fakeItemId) {
        if (offers.stream().anyMatch(offer -> FakeItems.isSpecificFakeItem(offer.getBaseCostA(), fakeItemId))) {
            return;
        }

        ItemStack fishMeat = FakeItems.createFakeItemStack(fakeItemId, FISH_MEAT_COST);
        CustomModelData modelData = fishMeat.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        ItemCost fishMeatCost = new ItemCost(
                fishMeat.typeHolder(),
                FISH_MEAT_COST,
                DataComponentExactPredicate.expect(DataComponents.CUSTOM_MODEL_DATA, modelData),
                fishMeat
        );

        offers.add(new MerchantOffer(
                fishMeatCost,
                new ItemStack(Items.EMERALD),
                MAX_USES,
                XP,
                PRICE_MULTIPLIER
        ));
    }
}
