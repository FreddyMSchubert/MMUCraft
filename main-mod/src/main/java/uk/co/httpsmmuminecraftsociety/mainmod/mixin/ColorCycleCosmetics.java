package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ColorCycleCosmetics
{
    @Inject(at = @At("HEAD"), method = "inventoryTick")
    void inventoryTick(Level level, Entity entity, EquipmentSlot equipmentSlot, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() != Items.CARVED_PUMPKIN) return;

    }
}
