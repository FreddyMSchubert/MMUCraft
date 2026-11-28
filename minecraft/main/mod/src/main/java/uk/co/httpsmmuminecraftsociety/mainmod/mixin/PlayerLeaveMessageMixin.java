package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.AdventBanhammerCharm;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerLeaveMessageMixin {
    @Redirect(
            method = "removePlayerFromWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void mainmod$leaveMessage(PlayerList playerList, Component message, boolean overlay) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).getPlayer();
        if (player.getGameProfile().name().equalsIgnoreCase("MMU_Admin")) return;
        if (AdventBanhammerCharm.consumeBonked(player)) {
            message = Component.empty().append(player.getDisplayName())
                    .append(Component.literal(" got bonked out of the game"))
                    .withStyle(ChatFormatting.YELLOW);
        }
        playerList.broadcastSystemMessage(message, overlay);
    }
}
