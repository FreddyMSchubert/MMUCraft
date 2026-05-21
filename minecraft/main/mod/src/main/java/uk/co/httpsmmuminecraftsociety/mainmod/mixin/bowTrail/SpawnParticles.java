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
    private static final String MAINMOD_TRAIL_KEY = "mainmod_arrow_trail";

    @Unique
    private static final float MAINMOD_TRAIL_BRIGHTNESS_BOOST = 1.35f;

    @Unique
    private static final float MAINMOD_TRAIL_SATURATION_BOOST = 1.25f;

    @Unique
    private static final int MAINMOD_TRAIL_PARTICLES_PER_TICK = 2;

    @Unique
    private static final int MAINMOD_TRAIL_MAX_AGE_TICKS = 30 * 20;

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
        if (self.tickCount > MAINMOD_TRAIL_MAX_AGE_TICKS) {
            return;
        }
        if (this.mainmod$trailSpec.isEmpty()) {
            return;
        }

        Vec3 pos = self.position();
        Vec3 vel = self.getDeltaMovement();

        for (int particle = 0; particle < MAINMOD_TRAIL_PARTICLES_PER_TICK; particle++) {
            double trailOffset = (particle + 0.5) / MAINMOD_TRAIL_PARTICLES_PER_TICK;
            double x = pos.x - vel.x * trailOffset;
            double y = pos.y - vel.y * trailOffset;
            double z = pos.z - vel.z * trailOffset;

            var dye = this.mainmod$trailSpec.pick(self.getRandom());
            int rgb = mainmod$boostTrailColor(dye.getTextureDiffuseColor());
            DustParticleOptions dust = new DustParticleOptions(rgb, 0.9f);

            serverLevel.sendParticles(
                    dust,
                    x, y, z,
                    1,
                    0.015, 0.015, 0.015,
                    0.0
            );
        }
    }

    @Unique
    private static int mainmod$boostTrailColor(int color) {
        int alpha = color & 0xFF000000;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        int average = (red + green + blue) / 3;

        red = mainmod$clampColor(Math.round((average + (red - average) * MAINMOD_TRAIL_SATURATION_BOOST) * MAINMOD_TRAIL_BRIGHTNESS_BOOST));
        green = mainmod$clampColor(Math.round((average + (green - average) * MAINMOD_TRAIL_SATURATION_BOOST) * MAINMOD_TRAIL_BRIGHTNESS_BOOST));
        blue = mainmod$clampColor(Math.round((average + (blue - average) * MAINMOD_TRAIL_SATURATION_BOOST) * MAINMOD_TRAIL_BRIGHTNESS_BOOST));

        return alpha | red << 16 | green << 8 | blue;
    }

    @Unique
    private static int mainmod$clampColor(int value) {
        return Math.max(0, Math.min(255, value));
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
