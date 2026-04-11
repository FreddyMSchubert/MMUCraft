package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
        return Component.empty()
                .append(Component.literal("(Don't worry, that is normal.)\n\n"))
                .append(Component.literal("Hi and welcome to the MMU Minecraft Society! ☺\n")
                        .withStyle(style -> style
                                .withColor(0xFFD166)
                                .withBold(true)))
                .append(Component.literal("This server uses a whitelist to keep hackers, demons and the "))
                .append(Component.literal("chupacabra")
                        .withStyle(style -> style.withObfuscated(true)))
                .append(Component.literal(" at bay.\n"))
                .append(Component.literal("You are not currently on the whitelist.\n\n")
                        .withStyle(style -> style
                                .withColor(0xFF6B6B)
                                .withBold(true)))
                .append(Component.literal("To change this in under 60 seconds, please verify your MMU email here:\n")
                .append(Component.literal("mmuminecraftsociety.co.uk\n\n")
                        .withStyle(style -> style
                                .withColor(0xA6DEFF)
                                .withUnderlined(true)
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("ermmm my bad i don't think it's clickable please type the url")
                                ))))
                .append(Component.literal("Once you're on the whitelist, you'll be able to join.\n"))
                .append(Component.literal("See you then! :)").withStyle(style -> style.withBold(true).withColor(0xFFD166))));
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
