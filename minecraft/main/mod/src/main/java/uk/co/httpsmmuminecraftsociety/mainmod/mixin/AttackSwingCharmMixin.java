package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class AttackSwingCharmMixin {
    @Inject(method = "handlePunch", at = @At("TAIL"))
    private void mainmod$onAttackSwing(ServerboundPunchPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        CharmsManager.onAttackSwing(listener.player, InteractionHand.MAIN_HAND);
    }
}
