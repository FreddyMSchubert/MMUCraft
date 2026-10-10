package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeView;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Shulker.class)
abstract class ShulkerViewMixin {
    @Inject(method = "findNewAttachment", at = @At("HEAD"), cancellable = true)
    private void killshift$keepViewAttachedToPlayer(CallbackInfo callback) {
        // Vanilla calls this even with AI disabled and can teleport the display away.
        if (((Shulker) (Object) this).entityTags().contains(ShapeView.VIEW_TAG)) {
            callback.cancel();
        }
    }
}
