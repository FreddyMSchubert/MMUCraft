package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.DimensionAccess;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerDimensionTeleportMixin {
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockDisabledDimension(
            TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> callback
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (DimensionAccess.allows(player, transition)) return;
        String dimension = transition.newLevel().dimension().equals(Level.END) ? "The End" : "The Nether";
        player.displayClientMessage(Component.literal(dimension + " is currently disabled."), true);
        callback.setReturnValue(null);
    }
}
