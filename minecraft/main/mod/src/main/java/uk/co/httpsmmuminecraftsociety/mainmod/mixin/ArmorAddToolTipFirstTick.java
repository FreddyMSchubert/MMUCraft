package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

@Mixin(ItemStack.class)
public class ArmorAddToolTipFirstTick
{
    @Inject(at = @At("HEAD"), method = "inventoryTick")
    private void init(CallbackInfo info) {
        ItemStack stack = (ItemStack)(Object)this;
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(CharmorManager.TOOLTIP_INITIALLY_POPULATED_BOOL)) {
                CharmorManager.updateArmorTooltip(stack);
            }
        }
    }
}
