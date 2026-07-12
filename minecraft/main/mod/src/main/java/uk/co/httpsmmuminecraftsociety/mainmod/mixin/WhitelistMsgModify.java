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
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.AuthGrpcService;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public class WhitelistMsgModify {
    @Unique
    private static Component customWhitelistMessage(String username, String code) {
        if (code == null)
            return Component.empty()
                    .append(Component.literal("(Don't worry, that is normal.)\n\n"))
                    .append(Component.literal("Hi " + username + ", welcome to the MMU Minecraft Society!\n")
                            .withStyle(style -> style
                                    .withColor(0xFFD166)
                                    .withBold(true)))
                    .append(Component.literal("This server uses a whitelist to keep hackers, demons and the "))
                    .append(Component.literal("chupacabra")
                            .withStyle(style -> style.withObfuscated(true)))
                    .append(Component.literal(" at bay.\n\n"))
                    .append(Component.literal("You are not currently on the whitelist.\n")
                            .withStyle(style -> style
                                    .withColor(0xFF6B6B)
                                    .withBold(true)))
                    .append(Component.literal("\nFinish signup here:\n"))
                    .append(Component.literal("mmuminecraftsociety.co.uk\n\n")
                            .withStyle(style -> style
                                    .withColor(0xA6DEFF)
                                    .withUnderlined(true)
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Component.literal("Type this URL into your browser.")
                                    ))))
                    .append(Component.literal("Once verified, try joining again.\n"))
                    .append(Component.literal("See you then!").withStyle(style -> style.withBold(true).withColor(0xFFD166)));
        else
            return Component.empty()
                    .append(Component.literal("Good job - almost finished.\n\n"))
                    .append(Component.literal("Your signup code is: \""))
                    .append(Component.literal(code)
                            .withStyle(style -> style
                                    .withColor(0xFFD166)
                                    .withBold(true)))
                    .append(Component.literal("\".\n\n"))
                    .append(Component.literal("Please input it on the website. :D"));
    }

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void replaceWhitelistKickMessage(
            SocketAddress address,
            NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir
    ) {
        PlayerList playerList = (PlayerList) (Object) this;
        boolean whitelisted = playerList.getWhiteList().isWhiteListed(nameAndId);

        AuthGrpcService.recordLoginAttempt(nameAndId, whitelisted);

        if (whitelisted) return;

        String code = AuthGrpcService.getPendingCodeFor(nameAndId.name());
        cir.setReturnValue(customWhitelistMessage(nameAndId.name(), code));
    }
}
