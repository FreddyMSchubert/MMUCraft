package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

@Mixin(ItemStack.class)
public abstract class MapStoredTooltipMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void mainmod$refreshMapTooltip(Level level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        SmallMaps.refreshTooltip(stack, level);
    }
}
