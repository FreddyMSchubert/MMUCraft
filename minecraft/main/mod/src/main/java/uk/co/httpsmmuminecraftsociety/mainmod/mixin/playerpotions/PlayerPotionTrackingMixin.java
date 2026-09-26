package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;

@Mixin(Entity.class)
abstract class PlayerPotionTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void mainmod$hideOwnerView(ServerPlayer observer, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerDisguises.isOwnerView((Entity) (Object) this, observer)) cir.setReturnValue(false);
    }
}
