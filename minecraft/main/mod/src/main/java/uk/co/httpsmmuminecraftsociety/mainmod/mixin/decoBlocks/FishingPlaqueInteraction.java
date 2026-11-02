package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPlaques;

@Mixin(ItemFrame.class)
public abstract class FishingPlaqueInteraction {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void mainmod$useFishingPlaque(Player player, InteractionHand hand, Vec3 hitPosition,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = FishingPlaques.interact((ItemFrame) (Object) this, player, hand);
        if (result != null) cir.setReturnValue(result);
    }
}
