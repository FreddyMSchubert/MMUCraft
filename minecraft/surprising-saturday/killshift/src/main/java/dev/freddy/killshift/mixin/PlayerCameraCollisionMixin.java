package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class PlayerCameraCollisionMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void killshift$keepCameraClearOfWalls(Pose pose,
            CallbackInfoReturnable<EntityDimensions> callback) {
        if ((Object) this instanceof ServerPlayer player) {
            EntityDimensions original = callback.getReturnValue();
            EntityDimensions safe = ShapeManager.cameraCollisionDimensions(player, original);
            if (safe != original) callback.setReturnValue(safe);
        }
    }
}
