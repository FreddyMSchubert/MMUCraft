package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.BedrockFormatting;

@Mixin(PlayerChatMessage.class)
abstract class FormattedChatMessageMixin {
    @Inject(method = "withUnsignedContent", at = @At("HEAD"), cancellable = true)
    private void retainFormattedContent(
            Component content, CallbackInfoReturnable<PlayerChatMessage> callback
    ) {
        PlayerChatMessage message = (PlayerChatMessage) (Object) this;
        if (BedrockFormatting.containsCode(message.signedContent())) {
            callback.setReturnValue(new PlayerChatMessage(
                    message.link(), message.signature(), message.signedBody(), content, message.filterMask()
            ));
        }
    }
}
