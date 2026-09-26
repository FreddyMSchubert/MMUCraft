package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerPotions;

@Mixin(PotionContents.class)
abstract class PlayerPotionHasEffectsMixin {
    @Inject(method = "hasEffects", at = @At("HEAD"), cancellable = true)
    private void mainmod$delivery(CallbackInfoReturnable<Boolean> cir) {
        if (PlayerPotions.identity((PotionContents) (Object) this) != null) cir.setReturnValue(true);
    }
}
