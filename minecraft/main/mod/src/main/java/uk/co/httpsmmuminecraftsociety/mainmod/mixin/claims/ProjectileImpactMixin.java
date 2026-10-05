package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(Projectile.class)
abstract class ProjectileImpactMixin {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void mainmod$discardDeniedImpact(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
        Projectile projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide() || hit.getType() == HitResult.Type.MISS) return;
        BlockPos pos = hit instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos() : BlockPos.containing(hit.getLocation());
        if (!ClaimsManager.allowProjectileAt(projectile, pos)) {
            projectile.discard();
            cir.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
