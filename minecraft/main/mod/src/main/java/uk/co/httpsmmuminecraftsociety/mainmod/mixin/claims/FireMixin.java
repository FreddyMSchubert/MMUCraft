package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(FireBlock.class)
abstract class FireMixin {
    @Inject(method = {"tick", "checkBurnOut"}, at = @At("HEAD"))
    private void mainmod$beginProtectedFireTick(CallbackInfo ci) {
        ClaimsManager.beginFireTick();
    }

    @Inject(method = {"tick", "checkBurnOut"}, at = @At("RETURN"))
    private void mainmod$endProtectedFireTick(CallbackInfo ci) {
        ClaimsManager.endFireTick();
    }
}
