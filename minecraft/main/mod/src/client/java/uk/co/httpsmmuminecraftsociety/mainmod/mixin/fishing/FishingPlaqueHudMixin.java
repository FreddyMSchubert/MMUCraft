package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPlaques;

@Mixin(Hud.class)
public abstract class FishingPlaqueHudMixin {
    @Shadow private int overlayMessageTime;

    @Inject(method = "setOverlayMessage", at = @At("TAIL"))
    private void mainmod$extendFishingPlaqueMessage(Component message, boolean animateColor, CallbackInfo ci) {
        if (FishingPlaques.ACTION_BAR_MARKER.equals(message.getStyle().getInsertion())) {
            overlayMessageTime = 80;
        }
    }
}
