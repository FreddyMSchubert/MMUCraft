package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;

@Mixin(ItemStack.class)
public abstract class TrailTooltip {
    @Unique private CustomData mainmod$lastTrailData;
    @Unique private ItemLore mainmod$lastTrailLore;

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void mainmod$updateTrailTooltip(CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!ParticleTrailData.supports(stack)) return;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        if (data == mainmod$lastTrailData && lore == mainmod$lastTrailLore) return;
        ParticleTrailData.updateTooltip(stack);
        mainmod$lastTrailData = data;
        mainmod$lastTrailLore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
    }
}
