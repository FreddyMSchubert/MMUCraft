package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.DimensionAccess;

@Mixin(Entity.class)
abstract class EntityDimensionTeleportMixin {
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockDisabledDimension(
            TeleportTransition transition,
            CallbackInfoReturnable<Entity> callback
    ) {
        if (!DimensionAccess.allows((Entity) (Object) this, transition)) callback.setReturnValue(null);
    }
}
