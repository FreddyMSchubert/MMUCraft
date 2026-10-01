package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;

@Mixin(Inventory.class)
public class SanitizePlayerArmorSlot {
    @Inject(method = "setItem", at = @At("HEAD"))
    private void mainmod$sanitizeArmor(int slot, ItemStack stack, CallbackInfo info) {
        if (!(((Inventory) (Object) this).player instanceof ServerPlayer)) return;
        var equipmentSlot = Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);
        if (equipmentSlot != null && equipmentSlot.isArmor()) GearEnchantmentSanitizer.sanitize(stack);
    }
}
