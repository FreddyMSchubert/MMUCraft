package dev.freddy.chunklocked.mixin;

import dev.freddy.chunklocked.EventPresentation;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EventChatNameMixin {
    @Shadow public ServerPlayer player;

    @ModifyArg(
            method = "broadcastChatMessage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"),
            index = 2
    )
    private ChatType.Bound chunklocked$chatName(ChatType.Bound bound) {
        return EventPresentation.chatName(player, bound);
    }
}
