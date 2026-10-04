package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(ServerLevel.class)
abstract class ServerLevelEntityMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void mainmod$guardClaimedEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ClaimsManager.allowProjectileHere(entity)) {
            entity.discard();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void mainmod$removeProjectileInsideClaim(Entity entity, CallbackInfo ci) {
        if (!ClaimsManager.allowProjectileHere(entity)) {
            entity.discard();
            ci.cancel();
        }
    }
}
