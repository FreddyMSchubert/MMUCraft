package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(ClientboundUpdateAdvancementsPacket.class)
public class AdvancementMoneyTooltip {
    @Mutable
    @Final
    @Shadow
    private List<AdvancementHolder> added;

    @Inject(method = "<init>(ZLjava/util/Collection;Ljava/util/Set;Ljava/util/Map;Z)V", at = @At("RETURN"))
    private void mainmod$addMoneyTooltips(
            boolean reset,
            Collection<AdvancementHolder> added,
            Set<Identifier> removed,
            Map<Identifier, AdvancementProgress> progress,
            boolean showAdvancements,
            CallbackInfo ci
    ) {
        this.added = this.added.stream()
                .map(this::withAugmentedDisplay)
                .toList();
    }

    private AdvancementHolder withAugmentedDisplay(AdvancementHolder holder) {
        Advancement advancement = holder.value();
        Optional<DisplayInfo> display = augmentedDisplay(holder.id(), advancement);
        if (display.isEmpty()) {
            return holder;
        }

        return new AdvancementHolder(
                holder.id(),
                new Advancement(
                        advancement.parent(),
                        display,
                        advancement.rewards(),
                        advancement.criteria(),
                        advancement.requirements(),
                        advancement.sendsTelemetryEvent()
                )
        );
    }

    private Optional<DisplayInfo> augmentedDisplay(Identifier advancementId, Advancement advancement) {
        return advancement.display().map(displayInfo -> {
            DisplayInfo copy = new DisplayInfo(
                    displayInfo.getIcon(),
                    displayInfo.getTitle(),
                    AdvancementMoney.appendMoneyReward(advancementId, displayInfo, advancement.rewards().experience()),
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
