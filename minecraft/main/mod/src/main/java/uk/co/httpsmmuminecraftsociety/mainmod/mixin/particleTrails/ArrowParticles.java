package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.TrailParticles;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.WeightedTrailSpec;

@Mixin(AbstractArrow.class)
public abstract class ArrowParticles {
    @Shadow protected abstract boolean isInGround();
    @Unique private WeightedTrailSpec mainmod$trail;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$spawnTrail(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!(arrow.level() instanceof ServerLevel) || arrow.isRemoved() || arrow.tickCount > 600) return;
        if (isInGround() && !arrow.isNoPhysics()) return;
        if (mainmod$trail == null) mainmod$trail = ParticleTrailData.getTrailSpec(arrow.getWeaponItem());
        TrailParticles.spawn(arrow, mainmod$trail, arrow.getOwner() instanceof ServerPlayer player ? player : null, false);
    }
}
