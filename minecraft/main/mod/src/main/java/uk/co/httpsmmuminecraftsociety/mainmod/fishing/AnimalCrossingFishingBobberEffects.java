package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.projectile.FishingHook;

public final class AnimalCrossingFishingBobberEffects {
    private AnimalCrossingFishingBobberEffects() {}

    public static void playFishArrival(ServerLevel level, Display.ItemDisplay display) {
        level.playSound(
                null, display.getX(), display.getY(), display.getZ(),
                SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.20F, 0.82F
        );
    }

    public static void playBounce(ServerLevel level, FishingHook hook) {
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM,
                SoundSource.PLAYERS, 0.22F, Mth.nextFloat(hook.getRandom(), 1.15F, 1.45F));
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.06D, hook.getZ(),
                3, 0.10D, 0.015D, 0.10D, 0.018D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.06D, hook.getZ(),
                2, 0.08D, 0.01D, 0.08D, 0.012D);
    }

    public static void playBite(ServerLevel level, FishingHook hook) {
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.GENERIC_SPLASH,
                SoundSource.PLAYERS, 0.95F, Mth.nextFloat(hook.getRandom(), 1.05F, 1.18F));
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM,
                SoundSource.PLAYERS, 0.72F, 0.65F);
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.12D, hook.getZ(),
                24, 0.36D, 0.06D, 0.36D, 0.095D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.10D, hook.getZ(),
                12, 0.28D, 0.025D, 0.28D, 0.04D);
    }

    public static void playBiteTick(ServerLevel level, FishingHook hook) {
        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.04D, hook.getZ(),
                2, 0.08D, 0.02D, 0.08D, 0.0D);
    }

    public static void playCatchStruggle(ServerLevel level, FishingHook hook, int animationTicks) {
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.024D, 0.0D));
        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.05D, hook.getZ(),
                4, 0.24D, 0.03D, 0.24D, 0.025D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.08D, hook.getZ(),
                3, 0.20D, 0.02D, 0.20D, 0.02D);
        if ((animationTicks & 1) != 0) return;

        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.GENERIC_SPLASH,
                SoundSource.PLAYERS, 0.34F, 1.45F);
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.08D, hook.getZ(),
                14, 0.34D, 0.05D, 0.34D, 0.055D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.08D, hook.getZ(),
                8, 0.26D, 0.02D, 0.26D, 0.035D);
    }

    public static void playScurry(ServerLevel level, FishingHook hook) {
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM,
                SoundSource.PLAYERS, 0.65F, 1.55F);
    }
}
