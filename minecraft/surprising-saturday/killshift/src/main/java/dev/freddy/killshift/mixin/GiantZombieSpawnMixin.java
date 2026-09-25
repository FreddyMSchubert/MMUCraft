package dev.freddy.killshift.mixin;

import dev.freddy.killshift.GiantSpawns;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
abstract class GiantZombieSpawnMixin {
    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void killshift$rollGiantSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            SpawnGroupData group,
            CallbackInfoReturnable<SpawnGroupData> callback
    ) {
        Zombie zombie = (Zombie) (Object) this;
        if (zombie.getType() == EntityTypes.ZOMBIE && level.getRandom().nextInt(100) == 0) {
            zombie.addTag(GiantSpawns.SPAWN_TAG);
        }
    }
}
