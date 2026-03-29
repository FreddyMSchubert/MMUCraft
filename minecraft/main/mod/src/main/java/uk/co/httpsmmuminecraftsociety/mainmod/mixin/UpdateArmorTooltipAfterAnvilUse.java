package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

// anvil changes what tooltip charmor should display, this detects it

@Mixin(AnvilMenu.class)
public abstract class UpdateArmorTooltipAfterAnvilUse {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void mainmod$refreshCharmorLore(CallbackInfo ci) {
        Slot outSlot = ((AbstractContainerMenu)(Object)this).getSlot(2);
        ItemStack out = outSlot.getItem();
        if (out.isEmpty()) return;

        if (out.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            ItemStack updated = CharmorManager.updateArmorTooltip(out.copy());
            outSlot.set(updated);
        }
    }
}
