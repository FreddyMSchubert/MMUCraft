package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguisePackets;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class PlayerPotionPacketMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> mainmod$observerView(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl connection) {
            return PlayerDisguisePackets.forObserver(connection.player, packet);
        }
        return packet;
    }
}
