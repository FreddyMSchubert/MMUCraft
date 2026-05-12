package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.util.Optional;

@Mixin(Advancement.class)
public class AdvancementMoneyTooltip {
    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void mainmod$writeMoneyTooltip(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        Advancement advancement = (Advancement) (Object) this;

        buf.writeOptional(advancement.parent(), FriendlyByteBuf::writeIdentifier);
        DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, augmentedDisplay(advancement));
        advancement.requirements().write(buf);
        buf.writeBoolean(advancement.sendsTelemetryEvent());
        ci.cancel();
    }

    private Optional<DisplayInfo> augmentedDisplay(Advancement advancement) {
        return advancement.display().map(displayInfo -> {
            DisplayInfo copy = new DisplayInfo(
                    displayInfo.getIcon(),
                    displayInfo.getTitle(),
                    AdvancementMoney.appendMoneyReward(displayInfo, advancement.rewards().experience()),
                    displayInfo.getBackground(),
                    displayInfo.getType(),
                    displayInfo.shouldShowToast(),
                    displayInfo.shouldAnnounceChat(),
                    displayInfo.isHidden()
            );
            copy.setLocation(displayInfo.getX(), displayInfo.getY());
            return copy;
        });
    }
}
