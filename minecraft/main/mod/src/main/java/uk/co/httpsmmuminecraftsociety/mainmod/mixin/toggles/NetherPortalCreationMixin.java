package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

@Mixin(PortalShape.class)
abstract class NetherPortalCreationMixin {
    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockDisabledNetherPortal(LevelAccessor level, CallbackInfo callback) {
        if (!FeatureToggles.isEnabled(FeatureToggles.NETHER)) callback.cancel();
    }
}
