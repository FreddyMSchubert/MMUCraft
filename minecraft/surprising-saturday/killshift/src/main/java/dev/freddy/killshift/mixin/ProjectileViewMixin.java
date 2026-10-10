package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class ProjectileViewMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void killshift$skipProtectedView(Entity target, CallbackInfoReturnable<Boolean> result) {
        if (!(target instanceof LivingEntity view)) return;
        LivingEntity player = ShapeManager.combatTarget(view);
        if (player == view) return;

        Entity owner = ((Projectile) (Object) this).getOwner();
        if (owner == player || owner instanceof Player attacker
                && player instanceof Player victim && !attacker.canHarmPlayer(victim)) {
            result.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), argsOnly = true)
    private HitResult killshift$hitViewOwner(HitResult hit) {
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof LivingEntity view)) return hit;
        LivingEntity player = ShapeManager.combatTarget(view);
        return player == view ? hit : new EntityHitResult(player, hit.getLocation());
    }
}
