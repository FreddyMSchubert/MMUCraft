package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.snowball.SnowballMachineGun;

@Mixin(CrossbowItem.class)
public abstract class SnowballMachineGunMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mainmod$useGun(Level level, Player player, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResult> cir) {
        if (SnowballMachineGun.isGun(player.getItemInHand(hand)))
            cir.setReturnValue(SnowballMachineGun.use(level, player, hand));
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void mainmod$fireGun(Level level, LivingEntity user, ItemStack stack, int ticksRemaining,
                                 CallbackInfo ci) {
        if (SnowballMachineGun.isGun(stack)) {
            SnowballMachineGun.tick(level, user, stack, ticksRemaining);
            ci.cancel();
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void mainmod$skipCrossbowLoading(ItemStack stack, Level level, LivingEntity user, int ticksRemaining,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (SnowballMachineGun.isGun(stack)) {
            SnowballMachineGun.release(stack);
            cir.setReturnValue(false);
        }
    }
}
