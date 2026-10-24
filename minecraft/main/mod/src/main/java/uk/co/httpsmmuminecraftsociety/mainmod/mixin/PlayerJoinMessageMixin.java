package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerJoinMessageMixin {
    @Redirect(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void mainmod$joinMessage(PlayerList playerList, Component message, boolean overlay,
                                     Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        if (player.getGameProfile().name().equalsIgnoreCase("MMU_Admin")) return;
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 1) {
            message = Component.translatableWithFallback(
                    "multiplayer.player.joined_first_time", "%s joined for the first time", player.getDisplayName()
            ).withStyle(ChatFormatting.YELLOW);
        }
        playerList.broadcastSystemMessage(message, overlay);
    }
}
