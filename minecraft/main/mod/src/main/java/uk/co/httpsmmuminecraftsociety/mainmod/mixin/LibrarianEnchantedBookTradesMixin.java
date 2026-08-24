package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LecternLibrarianTrades;

@Mixin(Villager.class)
public abstract class LibrarianEnchantedBookTradesMixin extends AbstractVillager {
    @Shadow private int updateMerchantTimer;
    @Shadow private boolean increaseProfessionLevelOnUpdate;
    @Shadow public abstract VillagerData getVillagerData();

    protected LibrarianEnchantedBookTradesMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "updateTrades", at = @At("RETURN"))
    private void removeGeneratedEnchantedBookTrades(ServerLevel level, CallbackInfo ci) {
        LecternLibrarianTrades.syncOffers((Villager) (Object) this, level);
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void refreshLecternBookTrade(Player player, CallbackInfo ci) {
        if (this.level() instanceof ServerLevel serverLevel) {
            LecternLibrarianTrades.syncOffers((Villager) (Object) this, serverLevel);
        }
    }

    @Inject(method = "rewardTradeXp", at = @At("RETURN"))
    private void mainmod$logRewardTradeXp(net.minecraft.world.item.trading.MerchantOffer offer, CallbackInfo ci) {
        MainMod.LOGGER.info(
                "[VillagerTradeDebug] rewarded uuid={} level={} serverXp={} offerXp={} timer={} pendingUpgrade={} trading={}",
                this.getUUID(), this.getVillagerData().level(), this.getVillagerXp(), offer.getXp(),
                this.updateMerchantTimer, this.increaseProfessionLevelOnUpdate, this.isTrading()
        );
    }

    @Inject(method = "setTradingPlayer", at = @At("RETURN"))
    private void mainmod$logTradingClosed(Player player, CallbackInfo ci) {
        if (player == null) {
            MainMod.LOGGER.info(
                    "[VillagerTradeDebug] menuClosed uuid={} level={} serverXp={} timer={} pendingUpgrade={} trading={}",
                    this.getUUID(), this.getVillagerData().level(), this.getVillagerXp(),
                    this.updateMerchantTimer, this.increaseProfessionLevelOnUpdate, this.isTrading()
            );
        }
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void mainmod$logUpgradeTimer(ServerLevel level, CallbackInfo ci) {
        if (!this.isTrading() && this.increaseProfessionLevelOnUpdate
                && (this.updateMerchantTimer == 40 || this.updateMerchantTimer == 20 || this.updateMerchantTimer == 1)) {
            MainMod.LOGGER.info(
                    "[VillagerTradeDebug] timerTick uuid={} level={} serverXp={} timer={}",
                    this.getUUID(), this.getVillagerData().level(), this.getVillagerXp(), this.updateMerchantTimer
            );
        }
    }

    @Inject(method = "increaseMerchantCareer", at = @At("HEAD"))
    private void mainmod$logLevelUpStarted(ServerLevel level, CallbackInfo ci) {
        MainMod.LOGGER.info(
                "[VillagerTradeDebug] levelUpStarted uuid={} level={} serverXp={}",
                this.getUUID(), this.getVillagerData().level(), this.getVillagerXp()
        );
    }

    @Inject(method = "increaseMerchantCareer", at = @At("RETURN"))
    private void mainmod$logLevelUpFinished(ServerLevel level, CallbackInfo ci) {
        MainMod.LOGGER.info(
                "[VillagerTradeDebug] levelUpFinished uuid={} level={} serverXp={} offers={}",
                this.getUUID(), this.getVillagerData().level(), this.getVillagerXp(), this.getOffers().size()
        );
    }
}
