package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class VanillaAdvancementAnnouncementMixin {
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mainmod$replaceVanillaAdvancementAnnouncement(
            Component message,
            boolean overlay,
            CallbackInfo ci
    ) {
        if (message.getContents() instanceof TranslatableContents translated
                && translated.getKey().startsWith("chat.type.advancement")) {
            ci.cancel();
        }
    }
}
