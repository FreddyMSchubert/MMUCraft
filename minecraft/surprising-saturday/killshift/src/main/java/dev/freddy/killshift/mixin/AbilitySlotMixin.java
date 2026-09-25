package dev.freddy.killshift.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class AbilitySlotMixin {
    @Final @Shadow public net.minecraft.world.Container container;
    @Shadow public abstract int getContainerSlot();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void killshift$keepAbilityInNinthSlot(Player player, CallbackInfoReturnable<Boolean> result) {
        if (container instanceof Inventory inventory && inventory.player == player
                && getContainerSlot() == 8) result.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void killshift$reserveNinthSlot(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (container instanceof Inventory && getContainerSlot() == 8) result.setReturnValue(false);
    }
}
