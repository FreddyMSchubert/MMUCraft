package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeView;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class UnsavedViewMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void killshift$neverSaveDisplay(CallbackInfoReturnable<Boolean> result) {
        if (((Entity) (Object) this).entityTags().contains(ShapeView.VIEW_TAG)) {
            result.setReturnValue(false);
        }
    }
}
