package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void killshift$targetHostileForms(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (mob.isNoAi() || mob.getTarget() != null || mob.tickCount % 10 != 0) return;
        boolean golem = mob instanceof IronGolem;
        boolean wolf = mob instanceof Wolf dog && !dog.isTame();
        boolean fox = mob instanceof Fox;
        if (!golem && !wolf && !fox) return;
        ServerPlayer nearest = null;
        double distance = 256.0;
        for (var person : mob.level().players()) {
            if (!(person instanceof ServerPlayer player)) continue;
            double candidate = mob.distanceToSqr(player);
            boolean prey = golem && ShapeManager.isGolemEnemy(player)
                    || wolf && ShapeManager.isForm(player, EntityTypes.SHEEP)
                    || fox && ShapeManager.isForm(player, EntityTypes.CHICKEN);
            if (candidate < distance && prey
                    && mob.canAttack(player) && mob.hasLineOfSight(player)) {
                nearest = player;
                distance = candidate;
            }
        }
        if (nearest != null) mob.setTarget(nearest);
    }
}
