package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

@Mixin(ItemStack.class)
public class ArmorAddToolTipFirstTick
{
    @Inject(at = @At("HEAD"), method = "inventoryTick")
    private void init(CallbackInfo info) {
        CharmorManager.initArmorTooltipIfUninitialized((ItemStack) (Object) this);
    }
}
