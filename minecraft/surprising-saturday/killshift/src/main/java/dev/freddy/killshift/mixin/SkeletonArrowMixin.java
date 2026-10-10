package dev.freddy.killshift.mixin;

import dev.freddy.killshift.AbilitySlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class SkeletonArrowMixin {
    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void killshift$useFormArrow(ItemStack weapon, CallbackInfoReturnable<ItemStack> result) {
        ItemStack arrow = AbilitySlot.arrowFor((Player) (Object) this, weapon);
        if (!arrow.isEmpty()) result.setReturnValue(arrow);
    }
}
