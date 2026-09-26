package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class ViewMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void killshift$freezeViewMovement(MoverType type, Vec3 motion, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (isControlledAquaticView(entity)) callback.cancel();
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"),
            cancellable = true)
    private void killshift$freezeViewVelocity(Vec3 motion, CallbackInfo callback) {
        if (motion.lengthSqr() > 0.0 && isControlledAquaticView((Entity) (Object) this)) {
            callback.cancel();
        }
    }

    private static boolean isControlledAquaticView(Entity entity) {
        return entity.entityTags().contains(ShapeView.VIEW_TAG)
                && (entity.getType() == EntityTypes.SQUID || entity.getType() == EntityTypes.GLOW_SQUID
                || entity.getType() == EntityTypes.SALMON);
    }
}
