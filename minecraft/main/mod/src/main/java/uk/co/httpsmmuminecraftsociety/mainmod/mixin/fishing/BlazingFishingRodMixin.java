package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingRodCast;

@Mixin(FishingRodItem.class)
public abstract class BlazingFishingRodMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void mainmod$rememberCastRod(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide() && player.fishing != null) {
            ((FishingRodCast) player.fishing).mainmod$setCastRod(player.getItemInHand(hand), hand);
        }
    }
}
