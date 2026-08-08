package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.List;

@Mixin(PistonStructureResolver.class)
abstract class PistonMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pistonPos;
    @Shadow @Final private Direction pistonDirection;

    @Shadow public abstract Direction getPushDirection();
    @Shadow public abstract List<BlockPos> getToPush();
    @Shadow public abstract List<BlockPos> getToDestroy();

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void mainmod$stopPistonAcrossClaimBoundary(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!ClaimsManager.isReady()) {
            cir.setReturnValue(false);
            return;
        }

        if (ClaimsManager.crossesClaimBoundary(level, pistonPos, pistonPos.relative(pistonDirection))) {
            cir.setReturnValue(false);
            return;
        }

        Direction pushDirection = getPushDirection();
        for (BlockPos pos : getToPush()) {
            if (ClaimsManager.crossesClaimBoundary(level, pos, pos.relative(pushDirection))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos pos : getToDestroy()) {
            if (ClaimsManager.crossesClaimBoundary(level, pistonPos, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
