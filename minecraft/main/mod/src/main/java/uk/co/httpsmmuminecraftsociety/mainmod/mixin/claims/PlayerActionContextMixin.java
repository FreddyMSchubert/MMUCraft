package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(ServerPlayerGameMode.class)
abstract class PlayerActionContextMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = {"destroyBlock", "useItem", "useItemOn"}, at = @At("HEAD"))
    private void mainmod$beginClaimAction(CallbackInfoReturnable<?> cir) {
        ClaimsManager.beginPlayerAction(player);
    }

    @Inject(method = {"destroyBlock", "useItem", "useItemOn"}, at = @At("RETURN"))
    private void mainmod$endClaimAction(CallbackInfoReturnable<?> cir) {
        ClaimsManager.endPlayerAction();
    }
}
