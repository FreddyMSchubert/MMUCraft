package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.Objects;

@Mixin(PistonBaseBlock.class)
abstract class PistonMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), cancellable = true)
    private void mainmod$stopPistonAcrossClaimBoundary(
            Level level,
            BlockPos pistonPos,
            Direction direction,
            boolean extending,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ClaimsManager.isReady()) {
            cir.setReturnValue(false);
            return;
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, direction, extending);
        if (!resolver.resolve()) return;

        String sourceClaim = ClaimsManager.claimIdAt(level, pistonPos);
        if (crosses(level, sourceClaim, pistonPos.relative(direction))) {
            cir.setReturnValue(false);
            return;
        }

        Direction pushDirection = resolver.getPushDirection();
        for (BlockPos pos : resolver.getToPush()) {
            if (crosses(level, sourceClaim, pos) || crosses(level, sourceClaim, pos.relative(pushDirection))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (crosses(level, sourceClaim, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    private static boolean crosses(Level level, String sourceClaim, BlockPos pos) {
        return !Objects.equals(sourceClaim, ClaimsManager.claimIdAt(level, pos));
    }
}
