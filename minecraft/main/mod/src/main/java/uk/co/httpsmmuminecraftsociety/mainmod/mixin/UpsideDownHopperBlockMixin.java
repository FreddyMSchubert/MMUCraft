package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHoppers;

@Mixin(HopperBlock.class)
public abstract class UpsideDownHopperBlockMixin {
    @Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
    private void mainmod$removeMarker(BlockState state, ServerLevel level, BlockPos pos, boolean moved, CallbackInfo ci) {
        UpsideDownHoppers.removeMarker(level, pos);
    }
}
