package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class FishMeatFishermanTradesCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        Path modDir = Path.of(args[0]);
        FakeItems.ID_MAP = Map.of(
                "cooked-red-fish", fish(modDir, "cooked-red-fish"),
                "cooked-white-fish", fish(modDir, "cooked-white-fish")
        );

        MerchantOffers offers = new MerchantOffers();
        FishMeatFishermanTrades.syncOffer(offers, "cooked-red-fish");
        FishMeatFishermanTrades.syncOffer(offers, "cooked-white-fish");
        assert offers.size() == 2;
        assert roundTripCost(offers.get(0)).getBaseCostA().getHoverName().getString().equals("Cooked Red Fish");
        assert roundTripCost(offers.get(1)).getBaseCostA().getHoverName().getString().equals("Cooked White Fish");

        ItemStack redFish = FakeItems.createFakeItemStack("cooked-red-fish", 6);
        ItemCost oldCost = new ItemCost(redFish.typeHolder(), 6,
                DataComponentExactPredicate.expect(DataComponents.CUSTOM_MODEL_DATA,
                        redFish.get(DataComponents.CUSTOM_MODEL_DATA)), redFish);
        MerchantOffer oldOffer = new MerchantOffer(oldCost, java.util.Optional.empty(),
                new ItemStack(Items.EMERALD), 5, 16, 2, 0.05F, 3);
        oldOffer.setSpecialPriceDiff(-1);
        offers.set(0, roundTripCost(oldOffer));
        FishMeatFishermanTrades.syncOffer(offers, "cooked-red-fish");
        assert offers.size() == 2;
        MerchantOffer repaired = roundTripCost(offers.get(0));
        assert repaired.getBaseCostA().getHoverName().getString().equals("Cooked Red Fish");
        assert repaired.getItemCostA().test(redFish);
        assert repaired.getUses() == 5 && repaired.getDemand() == 3;
        assert repaired.getSpecialPriceDiff() == -1;
        FishMeatFishermanTrades.syncOffer(offers, "cooked-red-fish");
        assert offers.size() == 2;
    }

    private static FakeItem fish(Path modDir, String id) throws Exception {
        Path path = modDir.resolve("../data/data/items/basic/food/" + id + "/item.json");
        return FakeItem.fromJson(JsonParser.parseString(Files.readString(path)).getAsJsonObject(), path.toString());
    }

    private static MerchantOffer roundTripCost(MerchantOffer offer) {
        ItemCost cost = ItemCost.CODEC.parse(JsonOps.INSTANCE,
                ItemCost.CODEC.encodeStart(JsonOps.INSTANCE, offer.getItemCostA()).getOrThrow()).getOrThrow();
        MerchantOffer roundTripped = new MerchantOffer(cost, offer.getItemCostB(), offer.getResult(), offer.getUses(),
                offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier(), offer.getDemand());
        roundTripped.setSpecialPriceDiff(offer.getSpecialPriceDiff());
        return roundTripped;
    }
}
