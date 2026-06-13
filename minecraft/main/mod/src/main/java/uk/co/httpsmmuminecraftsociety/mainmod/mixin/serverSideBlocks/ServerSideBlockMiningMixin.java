package uk.co.httpsmmuminecraftsociety.mainmod.mixin.serverSideBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.serverSideBlocks.ServerSideBlocks;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerSideBlockMiningMixin {
    @Shadow
    private ServerLevel level;

    @Redirect(
            method = "destroyBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canUseGameMasterBlocks()Z")
    )
    private boolean mainmod$allowServerSideBlockDestroy(ServerPlayer player, BlockPos pos) {
        return player.canUseGameMasterBlocks() || ServerSideBlocks.isServerSideBlock(this.level.getBlockState(pos));
    }

    @Redirect(
            method = "destroyBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z")
    )
    private boolean mainmod$allowServerSideBlockDrops(ServerPlayer player, BlockState state) {
        return ServerSideBlocks.isServerSideBlock(state) || player.hasCorrectToolForDrops(state);
    }

    @Redirect(
            method = "handleBlockBreakAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
            )
    )
    private float mainmod$getServerSideBlockDestroyProgress(
            BlockState state,
            Player player,
            BlockGetter level,
            BlockPos pos
    ) {
        if (ServerSideBlocks.isServerSideBlock(state)) {
            return ServerSideBlocks.getDestroyProgress(state);
        }

        return state.getDestroyProgress(player, level, pos);
    }
}
