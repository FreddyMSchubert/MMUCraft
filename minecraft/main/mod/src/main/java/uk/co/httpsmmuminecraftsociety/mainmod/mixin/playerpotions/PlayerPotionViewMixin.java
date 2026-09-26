package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;

@Mixin(LivingEntity.class)
abstract class PlayerPotionViewMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void mainmod$clickThrough(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mannequin view && PlayerDisguises.isView(view)) cir.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void mainmod$noCollision(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mannequin view && PlayerDisguises.isView(view)) cir.setReturnValue(false);
    }
}
