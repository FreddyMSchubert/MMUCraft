package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHopper;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHoppers;

@Mixin(BlockItem.class)
public abstract class UpsideDownHopperPlacementMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mainmod$placeUpsideDown(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()
                || ((BlockItem) (Object) this).getBlock() != Blocks.HOPPER
                || context.getClickedFace() != Direction.DOWN
                || !(context.getLevel() instanceof ServerLevel level)) return;

        if (level.getBlockEntity(context.getClickedPos()) instanceof HopperBlockEntity hopper) {
            ((UpsideDownHopper) hopper).mainmod$setUpsideDown(true);
            UpsideDownHoppers.ensureMarker(level, context.getClickedPos());
        }
    }
}
