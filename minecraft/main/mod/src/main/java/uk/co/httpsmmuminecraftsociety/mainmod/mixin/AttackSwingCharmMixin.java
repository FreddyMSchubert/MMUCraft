package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class AttackSwingCharmMixin {
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void mainmod$onAttackSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        CharmsManager.onAttackSwing(listener.player, packet.getHand());
    }
}
