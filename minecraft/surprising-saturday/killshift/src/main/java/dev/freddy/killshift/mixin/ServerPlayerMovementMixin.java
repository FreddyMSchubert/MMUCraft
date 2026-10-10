package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import dev.freddy.killshift.LandHopRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPlayerMovementMixin {
    @Inject(method = "handlePlayerPositionChange", at = @At("HEAD"), cancellable = true)
    private void killshift$limitLandHops(double x, double y, double z, float yRot, float xRot,
            boolean onGround, boolean horizontalCollision, CallbackInfo callback) {
        ServerGamePacketListenerImpl connection = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = connection.player;
        if (ShapeManager.frozenWhenWatched(player)
                && (x != player.getX() || z != player.getZ() || y > player.getY())) {
            connection.teleport(player.getX(), player.getY(), player.getZ(), yRot, xRot);
            callback.cancel();
            return;
        }
        double airSpeed = ShapeManager.landHopSpeed(player);
        if (airSpeed < 0.0) return;

        if (LandHopRule.exceedsLimit(x - player.getX(), y - player.getY(), z - player.getZ(),
                player.onGround(), onGround, airSpeed)) {
            connection.teleport(player.getX(), player.getY(), player.getZ(), yRot, xRot);
            callback.cancel();
        }
    }
}
