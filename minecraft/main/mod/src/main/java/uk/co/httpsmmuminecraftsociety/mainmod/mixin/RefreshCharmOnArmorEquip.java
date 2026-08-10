package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;

@Mixin(LivingEntity.class)
public class RefreshCharmOnArmorEquip
{
    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void refreshCharm(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo info) {
        if (slot.isArmor()) CharmsManager.refresh(newStack);
    }
}
