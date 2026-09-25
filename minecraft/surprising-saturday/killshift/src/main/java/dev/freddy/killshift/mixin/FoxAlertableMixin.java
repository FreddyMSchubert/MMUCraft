package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$FoxAlertableEntitiesSelector")
abstract class FoxAlertableMixin {
    @Inject(method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)Z",
            at = @At("HEAD"), cancellable = true)
    private void killshift$doNotFleeFromChickenForm(
            LivingEntity target, ServerLevel level, CallbackInfoReturnable<Boolean> result
    ) {
        if (target instanceof ServerPlayer player && ShapeManager.isForm(player, EntityTypes.CHICKEN)) {
            result.setReturnValue(false);
        }
    }
}
