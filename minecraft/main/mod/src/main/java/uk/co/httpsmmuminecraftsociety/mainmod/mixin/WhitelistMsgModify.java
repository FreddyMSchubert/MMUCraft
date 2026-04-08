package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public class WhitelistMsgModify
{
    @Unique
    private static Component customWhitelistMessage() {
        return Component.literal(
                "Hi and Welcome!\n" +
                        "To make this server super-secure, we use a whitelist to block hackers.\n" +
                        "You are not currently on the whitelist.\n" +
                        "This can be changed in under a minute by verifying your MMU email here:\n" +
                        "https://mmuminecraftsociety.co.uk/\n" +
                        "See you then ;)"
        );
    }

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void replaceWhitelistKickMessage(
            SocketAddress address,
            NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir
    ) {
        PlayerList playerList = (PlayerList) (Object) this;
        if (playerList.isWhiteListed(nameAndId)) return;
        cir.setReturnValue(customWhitelistMessage());
    }
}
