package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

@Mixin(Item.class)
public abstract class MapTooltipAndCraftingMixin {
    @Inject(method = "onCraftedPostProcess", at = @At("HEAD"))
    private void mainmod$resizeEmptyMap(ItemStack stack, Level level, CallbackInfo ci) {
        SmallMaps.processCraftedMap(stack, level);
    }
}
