package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementMoney {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "lambda$award$0", at = @At("HEAD"), cancellable = true)
    private void mainmod$deferAdvancementAnnouncement(
            AdvancementHolder advancementHolder,
            DisplayInfo displayInfo,
            CallbackInfo ci
    ) {
        ci.cancel();
    }

    @Inject(
            method = "award",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void mainmod$grantMoney(AdvancementHolder advancementHolder, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (advancementHolder.value().display().isEmpty()) {
            return;
        }

        ServerPlayer rewardedPlayer = this.player;
        grantMoney(
                rewardedPlayer,
                advancementHolder,
                advancementHolder.value().rewards(),
                PlayerStatsSync.isMember(rewardedPlayer)
        );
    }

    private void grantMoney(
            ServerPlayer rewardedPlayer,
            AdvancementHolder advancementHolder,
            AdvancementRewards rewards,
            boolean isMember
    ) {
        if (rewardedPlayer.hasDisconnected()) {
            return;
        }

        int reward = AdvancementMoney.rewardForAdvancement(
                advancementHolder.id(),
                rewards.experience(),
                isMember
        ).totalReward();
        boolean rewarded = reward > 0 && MoneyHelper.GainMoney(rewardedPlayer, reward);
        if (rewarded) {
            if ("minecraft".equals(advancementHolder.id().getNamespace()) || reward >= 30) {
                advancementHolder.value().display().ifPresent(display -> {
                    DiscordBridge.advancement(
                            rewardedPlayer,
                            discordAction(display),
                            display.getTitle().getString(),
                            reward
                    );
                });
            }
            GameplayGrpcService.recordMoneyEvent(
                    rewardedPlayer.getName().getString(),
                    rewardedPlayer.getUUID().toString(),
                    reward,
                    "advancement",
                    advancementHolder.id().toString(),
                    MoneyHelper.GetBalance(rewardedPlayer)
            ).exceptionally(error -> {
                MainMod.LOGGER.debug("Failed to record advancement dabloons for {}", rewardedPlayer.getName().getString(), error);
                return null;
            });

        }

        int awardedReward = rewarded ? reward : 0;
        if (shouldAnnounceAdvancement(advancementHolder, awardedReward)) {
            advancementHolder.value().display().ifPresent(display ->
                    announceAdvancement(rewardedPlayer, advancementHolder, display, awardedReward));
        }
    }

    private boolean shouldAnnounceAdvancement(AdvancementHolder advancementHolder, int reward) {
        return "minecraft".equals(advancementHolder.id().getNamespace()) || reward >= 30;
    }

    private void announceAdvancement(
            ServerPlayer rewardedPlayer,
            AdvancementHolder advancementHolder,
            DisplayInfo display,
            int reward
    ) {
        if (!display.shouldAnnounceChat()
                || !rewardedPlayer.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
            return;
        }

        String action = switch (display.getType()) {
            case TASK -> "achieved";
            case GOAL -> "reached the goal";
            case CHALLENGE -> "completed the challenge";
        };
        Component advancementName = Advancement.name(advancementHolder);

        for (ServerPlayer viewer : rewardedPlayer.level().getServer().getPlayerList().getPlayers()) {
            Component message;
            if (viewer.getUUID().equals(rewardedPlayer.getUUID())) {
                message = Component.literal("You " + action + " ").append(advancementName.copy());
                if (reward > 0) {
                    MoneyHelper.SendBalanceMessage(viewer, reward, message);
                    continue;
                }
            } else {
                message = Component.empty()
                        .append(rewardedPlayer.getDisplayName())
                        .append(Component.literal(" " + action + " "))
                        .append(advancementName.copy());
            }
            viewer.sendSystemMessage(message);
        }
    }

    private String discordAction(DisplayInfo display) {
        return switch (display.getType()) {
            case TASK -> "achieved";
            case GOAL -> "reached the goal";
            case CHALLENGE -> "completed the challenge";
        };
    }
}
