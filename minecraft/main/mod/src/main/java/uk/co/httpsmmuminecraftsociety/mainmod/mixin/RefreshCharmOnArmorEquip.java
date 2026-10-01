package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;

@Mixin(LivingEntity.class)
public class RefreshCharmOnArmorEquip
{
    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void refreshCharm(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo info) {
        if (slot.isArmor()) {
            if ((Object) this instanceof ServerPlayer) GearEnchantmentSanitizer.sanitize(newStack);
            CharmsManager.refresh(newStack);
        }
    }
}
