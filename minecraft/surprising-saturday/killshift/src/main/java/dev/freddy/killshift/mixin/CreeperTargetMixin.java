package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
abstract class CreeperTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void killshift$ignoreDisguisedPlayers(LivingEntity target, CallbackInfo callback) {
        Creeper creeper = (Creeper) (Object) this;
        if (target instanceof ServerPlayer player && ShapeManager.isFriendly(creeper, player)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void killshift$stopSwellAtDisguisedPlayers(CallbackInfo callback) {
        Creeper creeper = (Creeper) (Object) this;
        if (creeper.level().isClientSide()) return;
        LivingEntity target = creeper.getTarget();
        if (target instanceof ServerPlayer player && ShapeManager.isFriendly(creeper, player)) {
            creeper.setTarget(null);
        }
        if (creeper.getTarget() == null && creeper.getSwellDir() > 0 && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }
    }
}
