package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
abstract class ExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void mainmod$protectClaimedBlocks(CallbackInfoReturnable<List<BlockPos>> cir) {
        if (!ClaimsManager.isReady()) {
            cir.setReturnValue(List.of());
            return;
        }
        List<BlockPos> filtered = new ArrayList<>(cir.getReturnValue());
        filtered.removeIf(pos -> ClaimsManager.isClaimed(level, pos));
        cir.setReturnValue(filtered);
    }
}
