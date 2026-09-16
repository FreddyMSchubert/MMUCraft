package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.MapInvisibility;

@Mixin(MapItem.class)
public abstract class MapInvisibilityUpdateMixin {
    @Redirect(
            method = "inventoryTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/MapItem;update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V")
    )
    private void mainmod$updateWithInvisibleBlock(MapItem item, Level level, Entity entity,
                                                   MapItemSavedData data, ItemStack stack,
                                                   net.minecraft.server.level.ServerLevel serverLevel,
                                                   Entity inventoryEntity, EquipmentSlot slot) {
        MapInvisibility.beginUpdate(stack);
        try {
            item.update(level, entity, data);
        } finally {
            MapInvisibility.endUpdate();
        }
    }

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/LinkedHashMultiset;create()Lcom/google/common/collect/LinkedHashMultiset;")
    )
    private void mainmod$beginMapPixel(Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        MapInvisibility.beginPixel();
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getMapColor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/MapColor;")
    )
    private MapColor mainmod$observeMappedBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return MapInvisibility.observeMapColor(state, level, pos);
    }

    @ModifyArg(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z"),
            index = 2
    )
    private byte mainmod$makePixelTransparent(byte vanillaColor) {
        return MapInvisibility.applyTransparency(vanillaColor);
    }
}
