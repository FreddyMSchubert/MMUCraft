package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.miniblocks.MiniBlockCatalog;
import uk.co.httpsmmuminecraftsociety.mainmod.miniblocks.MiniBlockDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.ArrayList;
import java.util.List;

@Mixin(WanderingTrader.class)
public abstract class MiniBlockWanderingTraderMixin {
    private static final int TRADE_COUNT = 15;
    private static final int MAX_USES = 3;

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void mainmod$appendMiniBlockTrades(ServerLevel level, CallbackInfo callbackInfo) {
        mainmod$ensureMiniBlockTrades(level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mainmod$updateMiniBlockTrades(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        if (player.level() instanceof ServerLevel level) mainmod$ensureMiniBlockTrades(level);
    }

    @Unique
    private void mainmod$ensureMiniBlockTrades(ServerLevel level) {
        if (!FeatureToggles.isEnabled(FeatureToggles.WELCOMING)) return;

        WanderingTrader trader = (WanderingTrader) (Object) this;
        MerchantOffers offers = trader.getOffers();
        if (offers.stream().anyMatch(offer -> MiniBlockCatalog.isMiniBlockOutput(offer.getResult()))) return;
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
