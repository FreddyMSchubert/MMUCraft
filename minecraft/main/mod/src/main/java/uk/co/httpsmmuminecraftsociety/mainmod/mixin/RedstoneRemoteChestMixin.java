package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.RedstoneRemoteCharm;

@Mixin(ServerLevel.class)
abstract class RedstoneRemoteChestMixin {
    @Inject(method = "neighborChanged(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("TAIL"))
    private void mainmod$powerChestRemotes(BlockPos pos, Block sourceBlock, Orientation orientation, CallbackInfo ci) {
        RedstoneRemoteCharm.onChestNeighborUpdate((ServerLevel) (Object) this, pos);
    }

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
            at = @At("TAIL"))
    private void mainmod$powerChestRemotesDirect(BlockState state, BlockPos pos, Block sourceBlock,
                                                  Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
        RedstoneRemoteCharm.onChestNeighborUpdate((ServerLevel) (Object) this, pos);
    }
}
