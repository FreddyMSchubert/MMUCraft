package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(NetherPortalBlock.class)
abstract class NetherPortalClaimMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockUnpermittedPortal(BlockState state, Level level, BlockPos pos,
                                                 Entity entity, InsideBlockEffectApplier effects,
                                                 boolean emerged, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player
                && !ClaimsManager.canPerform(player, level, pos, ClaimsManager.NETHER_PORTALS)) {
            ci.cancel();
        }
    }
}
