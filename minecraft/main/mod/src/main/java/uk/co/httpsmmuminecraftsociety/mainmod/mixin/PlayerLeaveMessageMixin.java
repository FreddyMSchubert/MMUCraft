package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerLeaveMessageMixin {
    @Redirect(
            method = "removePlayerFromWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void mainmod$leaveMessage(PlayerList playerList, Component message, boolean overlay) {
        if (!((ServerGamePacketListenerImpl) (Object) this).getPlayer().getGameProfile().name()
                .equalsIgnoreCase("MMU_Admin")) {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }
}
