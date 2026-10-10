package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class WaterSensitiveFormMixin {
    @Inject(method = "isSensitiveToWater", at = @At("HEAD"), cancellable = true)
    private void killshift$inheritWaterSensitivity(CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerPlayer player && ShapeManager.isWaterSensitive(player)) {
            result.setReturnValue(true);
        }
    }
}
