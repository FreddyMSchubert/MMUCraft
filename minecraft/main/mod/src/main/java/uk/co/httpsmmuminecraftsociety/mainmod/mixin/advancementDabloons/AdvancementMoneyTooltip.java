package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket.PositionedAdvancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.util.List;
import java.util.Optional;

@Mixin(PlayerAdvancements.class)
public class AdvancementMoneyTooltip {
    @Shadow
    private ServerPlayer player;

    @ModifyArg(
            method = "flushDirty",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;<init>(ZLjava/util/List;Ljava/util/Set;Ljava/util/Map;Z)V"
            ),
            index = 1
    )
    private List<PositionedAdvancement> mainmod$addMoneyTooltips(List<PositionedAdvancement> added) {
        return added.stream()
                .map(entry -> new PositionedAdvancement(withAugmentedDisplay(entry.advancement()), entry.x(), entry.y()))
                .toList();
    }

    private AdvancementHolder withAugmentedDisplay(AdvancementHolder holder) {
        Advancement advancement = holder.value();
        Optional<DisplayInfo> display = augmentedDisplay(holder, advancement);
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

    private Optional<DisplayInfo> augmentedDisplay(AdvancementHolder holder, Advancement advancement) {
        return advancement.display().map(displayInfo -> {
            DisplayInfo copy = new DisplayInfo(
                    displayInfo.icon(),
                    displayInfo.title(),
                    AdvancementMoney.appendMoneyReward(
                            holder.id(),
                            displayInfo,
                            advancement.rewards().experience(),
                            PlayerStatsSync.isMember(this.player)
                    ),
                    displayInfo.background(),
                    displayInfo.type(),
                    displayInfo.showToast(),
                    displayInfo.announceToChat(),
                    displayInfo.hidden()
            );
            return copy;
        });
    }
}
