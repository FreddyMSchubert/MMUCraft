package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;

@Mixin(value = Level.class, priority = 900)
public abstract class BreakDecoBlockFrameWithLight {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void mainmod$rememberDecoBlockLight(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir,
            @Share("removedDecoBlockLight") LocalBooleanRef removedDecoBlockLight
    ) {
        removedDecoBlockLight.set(
                ((Level) (Object) this).getBlockState(pos).is(Blocks.LIGHT) && !state.is(Blocks.LIGHT)
        );
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void mainmod$breakDecoBlockFrame(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir,
            @Share("removedDecoBlockLight") LocalBooleanRef removedDecoBlockLight
    ) {
        if (removedDecoBlockLight.get() && cir.getReturnValue()) {
            DecoBlocksManager.breakFrameAfterLightRemoval((Level) (Object) this, pos);
        }
    }
}
