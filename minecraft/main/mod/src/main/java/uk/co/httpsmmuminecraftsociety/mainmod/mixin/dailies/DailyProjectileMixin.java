package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(Projectile.class)
public abstract class DailyProjectileMixin {
    @Inject(method = "deflect", at = @At("RETURN"))
    private void mainmod$recordFireballReflection(
            ProjectileDeflection deflection,
            Entity deflector,
            EntityReference<Entity> previousOwner,
            boolean fromAttack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue() && (Object)this instanceof LargeFireball && deflector instanceof ServerPlayer player) {
            DailyTaskManager.record(player, DailyTaskEvent.simple(DailySimpleEvent.REFLECT_GHAST_FIREBALL));
        }
    }

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"))
    private void mainmod$recordThrownHit(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
        Projectile projectile = (Projectile)(Object)this;
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof ServerPlayer target)
                || !(projectile.getOwner() instanceof ServerPlayer owner)
                || owner == target) return;

        String projectileId = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString();
        if (projectile.getType() == EntityTypes.SNOWBALL || projectile.getType() == EntityTypes.EGG) {
            DailyTaskManager.record(owner, DailyTaskEvent.of(DailyTaskEvent.Type.HIT_PLAYER_WITH_PROJECTILE, projectileId));
        }
    }
}
