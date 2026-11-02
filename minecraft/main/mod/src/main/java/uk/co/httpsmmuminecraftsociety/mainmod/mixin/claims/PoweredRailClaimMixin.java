package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(PoweredRailBlock.class)
abstract class PoweredRailClaimMixin {
    @WrapOperation(method = "updateState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean mainmod$allowRailPowerUpdate(Level level, BlockPos pos, BlockState expectedState,
                                                  Operation<Boolean> original) {
        ClaimsManager.beginRailPowerUpdate(level, pos, level.getBlockState(pos), expectedState);
        try {
            return original.call(level, pos, expectedState);
        } finally {
            ClaimsManager.endRailPowerUpdate();
        }
    }
}
