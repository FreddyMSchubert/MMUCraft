package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.snowball.SnowballMachineGun;

@Mixin(ItemStack.class)
public abstract class SnowballMachineGunMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mainmod$useGun(Level level, Player player, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResult> cir) {
        if (SnowballMachineGun.isGun((ItemStack) (Object) this))
            cir.setReturnValue(SnowballMachineGun.use(level, player, hand));
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void mainmod$fireGun(Level level, LivingEntity user, int ticksRemaining,
                                 CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (SnowballMachineGun.isGun(stack)) {
            SnowballMachineGun.tick(level, user, stack, ticksRemaining);
            ci.cancel();
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void mainmod$skipTridentThrow(Level level, LivingEntity user, int ticksRemaining,
                                          CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (SnowballMachineGun.isGun(stack)) {
            SnowballMachineGun.release(stack);
            ci.cancel();
        }
    }
}
