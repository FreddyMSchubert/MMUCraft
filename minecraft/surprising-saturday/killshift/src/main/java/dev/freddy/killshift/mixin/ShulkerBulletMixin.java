package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBullet.class)
abstract class ShulkerBulletMixin {
    @Shadow private EntityReference<Entity> finalTarget;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void killshift$dropShulkerTarget(CallbackInfo callback) {
        ShulkerBullet bullet = (ShulkerBullet) (Object) this;
        if (bullet.level().isClientSide() || finalTarget == null) return;
        Entity target = finalTarget.getEntity(bullet.level(), Entity.class);
        if (target instanceof ServerPlayer player && ShapeManager.isForm(player, EntityTypes.SHULKER)) {
            bullet.discard();
            callback.cancel();
        }
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void killshift$ignoreShulkerPlayers(Entity target, CallbackInfoReturnable<Boolean> result) {
        if (target instanceof ServerPlayer player && ShapeManager.isForm(player, EntityTypes.SHULKER)) {
            result.setReturnValue(false);
        }
    }
}
