package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void killshift$keepMobFamiliesFriendly(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> result
    ) {
        if (target instanceof ServerPlayer player && ShapeManager.isFriendly((Mob) (Object) this, player)) {
            result.setReturnValue(false);
        }
    }
}
