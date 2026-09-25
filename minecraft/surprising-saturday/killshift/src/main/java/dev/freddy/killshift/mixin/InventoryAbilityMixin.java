package dev.freddy.killshift.mixin;

import dev.freddy.killshift.AbilitySlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
abstract class InventoryAbilityMixin {
    @Shadow public abstract int getSelectedSlot();

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void killshift$keepNinthSlotOnDrop(boolean all, CallbackInfoReturnable<ItemStack> result) {
        if (getSelectedSlot() == 8) result.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void killshift$doNotDropAbilitySlot(CallbackInfo callback) {
        Inventory inventory = (Inventory) (Object) this;
        if (AbilitySlot.locked(inventory.getItem(8))) inventory.setItem(8, ItemStack.EMPTY);
    }
}
