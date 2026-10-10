package dev.freddy.killshift.mixin;

import dev.freddy.killshift.AbilitySlot;
import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMovementMixin {
    @Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
    private void killshift$noMobSprint(CallbackInfoReturnable<Boolean> result) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer server
                ? ShapeManager.frozenWhenWatched(server)
                || ShapeManager.restrictPlayerMovement(server) && !ShapeManager.canSprintAsMob(server)
                : AbilitySlot.blocksSprint(player)) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void killshift$noMobCrawlSwim(CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer server
                ? ShapeManager.restrictPlayerMovement(server) && !ShapeManager.canSwimAsMob(server)
                : AbilitySlot.blocksSwim(player)) {
            player.setSwimming(false);
            callback.cancel();
        }
    }
}
