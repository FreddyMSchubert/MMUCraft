package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;

import java.util.List;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementMoney {
    private static final List<String> CELEBRATIONS = List.of(
            "Yippieh",
            "Cha-ching",
            "Hooray",
            "Woo-hoo",
            "Yayy",
            "Wahoo",
            "Whee",
            "Wheeeeeeeeeeeeee",
            "Wheeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
            "Huzzah",
            "HOT DOG!",
            "Hallelujah!",
            "Whoopee",
            "Bingo",
            "Bravo",
            "Wow",

            "GG",
            "Number go up",
            "Monkey brain happy",
            "Go buy yourself something nice",
            "Don't spend it all in one go",
            "Penny for your thoughts",
            "W in chat",
            "Stonks",
            "LGTM",
            "TLDR",
            "Profit",
            "Cash Splash",
            "Mint condition",
            "Coin-grats",
            "Pocket Change",
            "Debit where debit is due",
            "Crypto? No thanks. Dabloons? Real sh*t.",
            "You're a dabloonatic",
            "Latest in Block Chain news",
            "And so the rich get richer",
            "It's gonna start trickling down aaany minute now",
            "I hereby cent-ence you to having a bit more money now",
            "Makes cents",
            "You're a coin-artist",

            "Need for spend",
            "Red Debt Redemption",
            "This is a triumph",
            "Achievement get",
            "It's-a payday",
            "Flawless economy",
            "May the coins be with you",
            "Mission passed",
            "Hey! Listen!",
            "Well excuuuuuuse me",
            "Let's-a go get paid",
            "Baba is rich",
            "Expecto Paytronum",
            "The empire pays back",
            "My precious",
            "Accio coins",
            "Great Scott",
            "With great power comes great profit",
            "With great funds comes great responsibility",
            "Dormammu, I've come to budget",
            "Wololo",
            "Loot acquired",
            "Gotta cash 'em all",
            "Winner winner dabloon dinner",
            "It's dangerous to go broke",
            "All your coins are belong to us",

            "I said something NICE, not EXPENSIVE"
    );

    @Shadow
    private ServerPlayer player;

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

        advancementHolder.value().display().ifPresent(display ->
                DiscordBridge.advancement(rewardedPlayer, display.getTitle().getString()));

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

            advancementHolder.value().display().ifPresent(displayInfo -> rewardedPlayer.sendSystemMessage(
                    Component.literal(randomCelebration(rewardedPlayer) + ": You received " + money + " dabloons for completing ")
                            .append(displayInfo.getTitle().getString() + ". :D")
            ));
        }
    }

    private String randomCelebration(ServerPlayer rewardedPlayer) {
        return CELEBRATIONS.get(rewardedPlayer.getRandom().nextInt(CELEBRATIONS.size()));
    }
}
