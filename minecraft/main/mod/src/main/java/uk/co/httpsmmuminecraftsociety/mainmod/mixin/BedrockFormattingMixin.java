package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.BedrockFormatting;

@Mixin(MutableComponent.class)
abstract class BedrockFormattingMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void parseBedrockFormatting(
            ComponentContents contents, CallbackInfoReturnable<MutableComponent> callback
    ) {
        if (contents instanceof PlainTextContents plain && BedrockFormatting.containsCode(plain.text())) {
            callback.setReturnValue(BedrockFormatting.parse(plain.text()));
        }
    }
}
