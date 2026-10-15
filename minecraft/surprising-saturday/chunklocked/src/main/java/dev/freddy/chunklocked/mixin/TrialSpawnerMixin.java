package dev.freddy.chunklocked.mixin;

import dev.freddy.chunklocked.EventGoals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
    @Inject(method = "setState", at = @At("HEAD"))
    private void chunklocked$activated(Level level, TrialSpawnerState state, CallbackInfo callback) {
        TrialSpawner spawner = (TrialSpawner) (Object) this;
        if (level instanceof ServerLevel serverLevel && state == TrialSpawnerState.ACTIVE
                && spawner.getState() != TrialSpawnerState.ACTIVE) {
            EventGoals.trialSpawnerActivated(serverLevel, spawner);
        }
    }
}
