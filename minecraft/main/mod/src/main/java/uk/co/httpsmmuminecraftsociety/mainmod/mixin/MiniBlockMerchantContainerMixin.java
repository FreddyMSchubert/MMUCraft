package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.miniblocks.MiniBlockCatalog;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

@Mixin(MerchantContainer.class)
abstract class MiniBlockMerchantContainerMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow private MerchantOffer activeOffer;
    @Shadow public abstract void setItem(int index, ItemStack stack);

    @Inject(method = "updateSellItem", at = @At("TAIL"))
    private void mainmod$gateMiniBlockTrade(CallbackInfo callback) {
        if (FeatureToggles.isEnabled(FeatureToggles.WELCOMING)
                || !(merchant instanceof WanderingTrader)
                || activeOffer == null) return;

        if (MiniBlockCatalog.isMiniBlockOutput(activeOffer.getResult())) {
            setItem(2, ItemStack.EMPTY);
        }
    }
}
