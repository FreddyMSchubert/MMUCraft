package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.miniblocks.MiniBlockCatalog;
import uk.co.httpsmmuminecraftsociety.mainmod.miniblocks.MiniBlockDefinition;

import java.util.ArrayList;
import java.util.List;

@Mixin(WanderingTrader.class)
public abstract class MiniBlockWanderingTraderMixin {
    private static final int TRADE_COUNT = 15;
    private static final int MAX_USES = 3;

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void mainmod$appendMiniBlockTrades(ServerLevel level, CallbackInfo callbackInfo) {
        WanderingTrader trader = (WanderingTrader) (Object) this;
        MerchantOffers offers = trader.getOffers();
        List<MiniBlockDefinition> candidates = new ArrayList<>(MiniBlockCatalog.definitions());
        RandomSource random = level.getRandom();

        for (int index = 0; index < TRADE_COUNT && !candidates.isEmpty(); index++) {
            MiniBlockDefinition definition = candidates.remove(random.nextInt(candidates.size()));
            offers.add(new MerchantOffer(
                    new ItemCost(definition.inputItem()),
                    definition.createOutput(),
                    MAX_USES,
                    0,
                    0.0F
            ));
        }
    }
}
