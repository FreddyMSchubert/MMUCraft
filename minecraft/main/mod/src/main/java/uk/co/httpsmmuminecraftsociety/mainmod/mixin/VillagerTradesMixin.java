package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FishMeatFishermanTrades;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LecternLibrarianTrades;

@Mixin(Villager.class)
public abstract class VillagerTradesMixin extends AbstractVillager {
    protected VillagerTradesMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "updateTrades", at = @At("RETURN"))
    private void modifyGeneratedTrades(ServerLevel level, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        LecternLibrarianTrades.syncOffers(villager, level);
        FishMeatFishermanTrades.appendOffers(villager);
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void refreshLecternBookTrade(Player player, CallbackInfo ci) {
        if (this.level() instanceof ServerLevel serverLevel) {
            Villager villager = (Villager) (Object) this;
            LecternLibrarianTrades.syncOffers(villager, serverLevel);
            FishMeatFishermanTrades.appendOffers(villager);
        }
    }
}
