package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.EnchantmentLock;

@Mixin(net.minecraft.world.inventory.GrindstoneMenu.class)
public abstract class GrindstoneRemoveEnchantmentLock
{
    @Inject(method = "computeResult", at = @At("RETURN"), cancellable = true)
    private void mainmod$removeLockedResultEnchantments(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!result.isEmpty()) {
            cir.setReturnValue(EnchantmentLock.removeAllEnchantmentsAndUnlock(result));
            return;
        }

        ItemStack onlyInput = first.isEmpty() ? second : second.isEmpty() ? first : ItemStack.EMPTY;
        if (!onlyInput.isEmpty()
                && onlyInput.getCount() == 1
                && (EnchantmentHelper.hasAnyEnchantments(onlyInput) || EnchantmentLock.isLocked(onlyInput))) {
            cir.setReturnValue(EnchantmentLock.removeAllEnchantmentsAndUnlock(onlyInput.copy()));
        }
    }

    @Inject(method = "removeNonCursesFrom", at = @At("HEAD"), cancellable = true)
    private void mainmod$removeLockedEnchantments(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(EnchantmentLock.removeAllEnchantmentsAndUnlock(stack));
    }
}
