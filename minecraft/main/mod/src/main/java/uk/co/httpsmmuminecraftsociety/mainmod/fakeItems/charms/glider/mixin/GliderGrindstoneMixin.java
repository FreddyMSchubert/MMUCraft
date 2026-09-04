package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;

@Mixin(GrindstoneMenu.class)
public abstract class GliderGrindstoneMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void mainmod$keepWingTypesSeparate(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
        if (!first.isEmpty() && !second.isEmpty() && GliderCharm.isGlider(first) != GliderCharm.isGlider(second)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
