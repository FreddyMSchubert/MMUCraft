package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(ServerLevel.class)
abstract class ServerLevelEntityMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void mainmod$guardClaimedEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ClaimsManager.allowEntitySpawn((ServerLevel) (Object) this, entity)) {
            // Some entities register themselves with their owner in their constructor. Ensure a rejected
            // spawn runs normal removal cleanup instead of leaving an owner pointing at an entity that was
            // never added to the level (FishingHook is one vanilla example).
            entity.discard();
            cir.setReturnValue(false);
        }
    }
}
