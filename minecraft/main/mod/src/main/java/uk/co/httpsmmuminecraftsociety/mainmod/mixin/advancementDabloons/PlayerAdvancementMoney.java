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
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

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

        AdvancementRewards rewards = advancementHolder.value().rewards();
        int money = AdvancementMoney.moneyForAdvancement(advancementHolder.id(), rewards.experience());
        MoneyHelper.GainMoney(this.player, money);
        if (money > 0) {
            GameplayGrpcService.recordMoneyEvent(
                    this.player.getName().getString(),
                    money,
                    "earned",
                    "advancement",
                    advancementHolder.id().toString(),
                    MoneyHelper.GetBalance(this.player)
            ).exceptionally(error -> {
                MainMod.LOGGER.debug("Failed to record advancement dabloons for {}", this.player.getName().getString(), error);
                return null;
            });
        }

        advancementHolder.value().display().ifPresent(displayInfo -> this.player.sendSystemMessage(
                Component.literal(randomCelebration() + ": You received " + money + " dabloons for completing ")
                        .append(displayInfo.getTitle().getString() + ". :D")
        ));
    }

    private String randomCelebration() {
        return CELEBRATIONS.get(this.player.getRandom().nextInt(CELEBRATIONS.size()));
    }
}
