package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class PlayerSprintMixin {
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void killshift$limitFormSprint(boolean sprinting, CallbackInfo callback) {
        if (sprinting && (Object) this instanceof ServerPlayer player
                && (ShapeManager.frozenWhenWatched(player)
                || ShapeManager.restrictPlayerMovement(player) && !ShapeManager.canSprintAsMob(player))) {
            callback.cancel();
        }
    }
}
