package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
abstract class ChargedCreeperSpawnMixin {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_IS_POWERED;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mainmod$chargeSomeNewCreepers(EntityType<? extends Creeper> type, Level level, CallbackInfo callback) {
        if (level instanceof ServerLevel && level.getRandom().nextDouble() < 0.002) {
            ((Creeper) (Object) this).getEntityData().set(DATA_IS_POWERED, true);
        }
    }
}
