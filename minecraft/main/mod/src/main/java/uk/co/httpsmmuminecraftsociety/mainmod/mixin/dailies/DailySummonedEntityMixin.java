package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.SummonedEntityTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(SummonedEntityTrigger.class)
public abstract class DailySummonedEntityMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordNearestPlayer(ServerPlayer summoner, Entity entity, CallbackInfo ci) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        ServerPlayer nearest = level.players().stream()
                .min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(entity)))
                .orElse(null);
        if (nearest != null) {
            DailyTaskManager.record(nearest, DailyTaskEvent.of(
                    DailyTaskEvent.Type.CREATE_GOLEM,
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
            ));
        }
    }
}
