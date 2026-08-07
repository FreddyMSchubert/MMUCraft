package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(LivingEntity.class)
public abstract class DailyEffectMixin {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void mainmod$recordEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && (Object)this instanceof ServerPlayer player) {
            effect.getEffect().unwrapKey().ifPresent(key -> {
                String effectId = key.identifier().toString();
                DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.RECEIVE_EFFECT, effectId));
                if (effect.getEffect().equals(MobEffects.HERO_OF_THE_VILLAGE)) {
                    DailyTaskManager.record(player, DailyTaskEvent.simple(DailySimpleEvent.DEFEAT_RAID));
                }
            });
        }
    }
}
