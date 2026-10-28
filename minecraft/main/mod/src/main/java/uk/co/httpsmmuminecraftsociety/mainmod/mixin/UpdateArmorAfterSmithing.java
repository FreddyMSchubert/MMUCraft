package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

@Mixin(SmithingMenu.class)
public abstract class UpdateArmorAfterSmithing {
    @Inject(method = "createResult", at = @At("TAIL"))
    private void mainmod$refreshArmor(CallbackInfo ci) {
        Slot output = ((AbstractContainerMenu) (Object) this).getSlot(3);
        ItemStack result = output.getItem();
        if (CharmorManager.canMinifyArmor(result)) {
            output.set(CharmorManager.updateArmorTooltip(result.copy()));
        }
    }
}
