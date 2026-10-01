package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(Level.class)
abstract class LevelMutationMixin {
    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mainmod$guardBoatHarvestInClaim(
            BlockPos pos,
            boolean drop,
            Entity destroyer,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (destroyer instanceof AbstractBoat && ClaimsManager.isClaimed((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mainmod$guardClaimedBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ClaimsManager.allowBlockMutation((Level) (Object) this, pos, state)) cir.setReturnValue(false);
    }
}
