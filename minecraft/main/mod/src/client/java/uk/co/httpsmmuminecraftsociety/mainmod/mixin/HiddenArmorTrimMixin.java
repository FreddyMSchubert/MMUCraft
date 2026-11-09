package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

@Mixin(HumanoidArmorLayer.class)
public class HiddenArmorTrimMixin {
    @ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack mainmod$hideArmorTrim(ItemStack stack) {
        if (!stack.has(DataComponents.TRIM) || !CharmorManager.armorHidden(stack)) return stack;
        ItemStack rendered = stack.copy();
        rendered.remove(DataComponents.TRIM);
        return rendered;
    }
}
