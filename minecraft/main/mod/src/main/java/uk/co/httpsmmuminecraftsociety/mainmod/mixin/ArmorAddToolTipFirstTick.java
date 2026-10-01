package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

@Mixin(ItemStack.class)
public class ArmorAddToolTipFirstTick
{
    @Inject(at = @At("HEAD"), method = "inventoryTick")
    private void init(Level level, Entity entity, EquipmentSlot slot, CallbackInfo info) {
        ItemStack stack = (ItemStack) (Object) this;
        if (entity instanceof ServerPlayer) GearEnchantmentSanitizer.sanitize(stack);
        CharmorManager.initArmorTooltipIfUninitialized(stack);
    }
}
