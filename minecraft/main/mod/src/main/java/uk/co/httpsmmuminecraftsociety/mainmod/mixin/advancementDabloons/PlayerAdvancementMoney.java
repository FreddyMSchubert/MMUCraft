package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementMoney {
    @Shadow
    private ServerPlayer player;

    @Redirect(
            method = "lambda$award$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void mainmod$suppressVanillaAdvancementAnnouncement(
            PlayerList playerList,
            Component message,
            boolean overlay
    ) {
        // Vanilla broadcasts from the display callback, not directly from award().
        // @Redirect replaces only that broadcast call, so the rest of award() still runs.
        // grantMoney() sends the combined message after it resolves the membership multiplier.
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
        var membership = PlayerStatsSync.syncNow(rewardedPlayer);
        AdvancementRewards rewards = advancementHolder.value().rewards();
        membership.thenAccept(isMember -> rewardedPlayer.level().getServer().execute(
                () -> grantMoney(rewardedPlayer, advancementHolder, rewards, isMember)
        ));
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

        int money = AdvancementMoney.rewardForAdvancement(
                advancementHolder.id(),
                rewards.experience(),
                isMember
        ).totalReward();
        if (money > 0 && MoneyHelper.GainMoney(rewardedPlayer, money)) {
            GameplayGrpcService.recordMoneyEvent(
                    rewardedPlayer.getName().getString(),
                    rewardedPlayer.getUUID().toString(),
                    money,
                    "earned",
                    "advancement",
                    advancementHolder.id().toString(),
                    MoneyHelper.GetBalance(rewardedPlayer)
            ).exceptionally(error -> {
                MainMod.LOGGER.debug("Failed to record advancement dabloons for {}", rewardedPlayer.getName().getString(), error);
                return null;
            });

            advancementHolder.value().display().ifPresent(displayInfo ->
                    sendAdvancementAnnouncement(rewardedPlayer, advancementHolder, displayInfo, money)
            );
        } else {
            advancementHolder.value().display().ifPresent(displayInfo ->
                    deliverAdvancementMessage(
                            rewardedPlayer,
                            displayInfo,
                            displayInfo.getType().createAnnouncement(advancementHolder, rewardedPlayer),
                            true
                    )
            );
        }
    }

    private void sendAdvancementAnnouncement(
            ServerPlayer rewardedPlayer,
            AdvancementHolder advancementHolder,
            DisplayInfo displayInfo,
            int money
    ) {
        AdvancementType type = displayInfo.getType();
        String action = switch (type) {
            case TASK -> " making the advancement ";
            case GOAL -> " reaching the goal ";
            case CHALLENGE -> " completing the challenge ";
        };
        Component message = Component.empty()
                .append(rewardedPlayer.getDisplayName())
                .append(" has earned ")
                .append(Integer.toString(money)).withStyle(money > 50 ? ChatFormatting.GOLD : ChatFormatting.RESET)
                .append(" dabloons" + action)
                .append(Advancement.name(advancementHolder));

        boolean announceToEveryone = advancementHolder.id().getNamespace().equals("minecraft") || money >= 30;
        deliverAdvancementMessage(rewardedPlayer, displayInfo, message, announceToEveryone);
    }

    private void deliverAdvancementMessage(
            ServerPlayer rewardedPlayer,
            DisplayInfo displayInfo,
            Component message,
            boolean announceToEveryone
    ) {
        if (displayInfo.shouldAnnounceChat() && announceToEveryone
                && rewardedPlayer.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
            rewardedPlayer.level().getServer().getPlayerList().broadcastSystemMessage(message, false);
        } else {
            rewardedPlayer.sendSystemMessage(message);
        }
    }
}
