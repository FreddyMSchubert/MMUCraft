package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.TrailParticles;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.WeightedTrailSpec;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkParticles {
    @Unique private WeightedTrailSpec mainmod$trail;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$spawnTrail(CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (!(rocket.level() instanceof ServerLevel) || rocket.isRemoved() || !rocket.isShotAtAngle()) return;
        if (mainmod$trail == null) mainmod$trail = ParticleTrailData.getTrailSpec(rocket.getItem());
        TrailParticles.spawn(rocket, mainmod$trail, rocket.getOwner() instanceof ServerPlayer player ? player : null, false);
    }
}
