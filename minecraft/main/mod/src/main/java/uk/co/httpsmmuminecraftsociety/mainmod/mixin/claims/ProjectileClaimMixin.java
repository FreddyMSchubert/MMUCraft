package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
abstract class ProjectileClaimMixin {
    @Inject(method = "getHitResult", at = @At("RETURN"), cancellable = true)
    private static void mainmod$discardAtClaimBoundary(
            Vec3 start,
            Entity entity,
            Predicate<Entity> predicate,
            Vec3 movement,
            Level level,
            float margin,
            ClipContext.Block clipType,
            CallbackInfoReturnable<HitResult> cir
    ) {
        if (level.isClientSide() || !(entity instanceof Projectile projectile)) return;
        BlockHitResult deniedEntry = ClaimsManager.deniedProjectileEntry(projectile, start, movement);
        HitResult vanilla = cir.getReturnValue();
        if (deniedEntry != null && (vanilla == null
                || deniedEntry.getLocation().distanceToSqr(start) <= vanilla.getLocation().distanceToSqr(start))) {
            projectile.discard();
            cir.setReturnValue(BlockHitResult.miss(start,
                    Direction.getApproximateNearest(movement).getOpposite(), BlockPos.containing(start)));
        }
    }
}
