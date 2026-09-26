package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;

@Mixin(LivingEntity.class)
abstract class PlayerPotionSwingMixin {
    @Inject(method = "swing", at = @At("TAIL"))
    private void mainmod$mirrorSwing(InteractionHand hand, SwingAnimation animation, boolean forced,
                                      CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && cir.getReturnValue()) {
            PlayerDisguises.swing(player, hand, animation);
        }
    }
}
