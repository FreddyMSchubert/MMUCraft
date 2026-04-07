package uk.co.httpsmmuminecraftsociety.mainmod.mixin.bowTrail;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.ArrowTrailAccess;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.WeightedTrailSpec;

@Mixin(AbstractArrow.class)
public abstract class SpawnParticles implements ArrowTrailAccess
{
    @Unique
    private static final int PARTICLES_EVERY_X_TICKS = 3;

    @Unique
    private static final String MAINMOD_TRAIL_KEY = "mainmod_arrow_trail";

    @Unique
    private WeightedTrailSpec mainmod$trailSpec = WeightedTrailSpec.EMPTY;

    @Override
    public void mainmod$setTrailSpec(WeightedTrailSpec spec) {
        this.mainmod$trailSpec = spec == null ? WeightedTrailSpec.EMPTY : spec;
    }

    @Override
    public WeightedTrailSpec mainmod$getTrailSpec() {
        return this.mainmod$trailSpec;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$spawnTrail(CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;

        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (self.onGround()) {
            return;
        }
        if (this.mainmod$trailSpec.isEmpty()) {
            return;
        }

        // optional small perf throttle, only on even ticks
        if ((self.tickCount % PARTICLES_EVERY_X_TICKS) != 0) {
            return;
        }

        var dye = this.mainmod$trailSpec.pick(self.getRandom());
        int rgb = dye.getTextureDiffuseColor();

        Vec3 pos = self.position();
        Vec3 vel = self.getDeltaMovement();

        double x = pos.x - vel.x * 0.25;
        double y = pos.y - vel.y * 0.25;
        double z = pos.z - vel.z * 0.25;

        DustParticleOptions dust = new DustParticleOptions(rgb, 0.9f);

        serverLevel.sendParticles(
                dust,
                x, y, z,
                1,
                0.015, 0.015, 0.015,
                0.0
        );
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mainmod$writeTrailData(ValueOutput output, CallbackInfo ci) {
        if (this.mainmod$trailSpec.isEmpty()) {
            return;
        }
        output.putString(MAINMOD_TRAIL_KEY, this.mainmod$trailSpec.serialize());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void yourmod$readTrailData(ValueInput input, CallbackInfo ci) {
        if (!input.contains(MAINMOD_TRAIL_KEY)) {
            return;
        }
        this.mainmod$trailSpec = WeightedTrailSpec.deserialize(input.getString(MAINMOD_TRAIL_KEY).get());
    }
}
