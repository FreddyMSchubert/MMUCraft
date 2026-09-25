package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMovementMixin {
    @Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
    private void killshift$noMobSprint(CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerPlayer player && ShapeManager.restrictPlayerMovement(player)) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void killshift$noMobCrawlSwim(CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player && ShapeManager.restrictPlayerMovement(player)) {
            player.setSwimming(false);
            callback.cancel();
        }
    }
}
