package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
final class GameplayMoneyOperations {
    private GameplayMoneyOperations() {}

    static GrantDailyLoginBonusResponse grantDailyLoginBonusOnMainThread(GrantDailyLoginBonusRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return GrantDailyLoginBonusResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to receive the Dabloons.")
                    .build();
        }

        int amount = Math.max(0, request.getAmount());
        if (!MoneyHelper.GainMoney(player, amount)) {
            return GrantDailyLoginBonusResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(true)
                    .setMessage("Could not grant the daily login bonus.")
                    .build();
        }

        if ("daily_completion".equals(request.getSource())) {
            DiscordBridge.playerEvent("dailies", player,
                    "completed all of today's dailies.");
        }

        MoneyHelper.SendBalanceMessage(player, amount,
                "daily_completion".equals(request.getSource()) ? "Completed all dailies" : "Daily login reward");
        return GrantDailyLoginBonusResponse.newBuilder()
                .setGranted(true)
                .setOnline(true)
                .setMessage("You received " + amount + " Dabloons.")
                .build();
    }

    static GrantGiftCodeMoneyResponse grantGiftCodeMoneyOnMainThread(GrantGiftCodeMoneyRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GrantGiftCodeMoneyResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to redeem a gift code.")
                    .build();
        }

        int amount = Math.max(0, request.getAmountDabloons());
        if (amount == 0 || !MoneyHelper.GainMoney(player, amount)) {
            return GrantGiftCodeMoneyResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Could not grant the gift code Dabloons.")
                    .build();
        }

        int balance = MoneyHelper.GetBalance(player);
        MoneyHelper.SendBalanceMessage(player, amount, "Redeemed gift code");
        return GrantGiftCodeMoneyResponse.newBuilder()
                .setGranted(true)
                .setOnline(true)
                .setBalanceDabloons(balance)
                .setMessage("Gift code redeemed for " + amount + " Dabloons.")
                .build();
    }

    static GrantKnowledgeReadMoneyResponse grantKnowledgeReadMoneyOnMainThread(GrantKnowledgeReadMoneyRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GrantKnowledgeReadMoneyResponse.newBuilder().setGranted(false)
                    .setMessage("You have to be online on the server to receive Dabloons.").build();
        }

        int amount = Math.max(0, request.getAmountDabloons());
        if (amount == 0 || !MoneyHelper.GainMoney(player, amount)) {
            return GrantKnowledgeReadMoneyResponse.newBuilder().setGranted(false)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player)).setMessage("Could not grant the Dabloons.").build();
        }

        int balance = MoneyHelper.GetBalance(player);
        MoneyHelper.SendBalanceMessage(player, amount, "Read knowledge");
        return GrantKnowledgeReadMoneyResponse.newBuilder().setGranted(true)
                .setBalanceDabloons(balance).setMessage(request.getMessage()).build();
    }

    static PurchaseExternalPlayerInviteResponse purchaseExternalPlayerInviteOnMainThread(
            PurchaseExternalPlayerInviteRequest request
    ) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return PurchaseExternalPlayerInviteResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setMessage("The responsible player must be online to pay for this invitation.")
                    .build();
        }

        int price = PlayerStatsSync.isMember(player)
                ? MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS
                : NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS;
        int balance = MoneyHelper.GetBalance(player);
        if (balance < price || !MoneyHelper.ReduceMoney(player, price)) {
            return PurchaseExternalPlayerInviteResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("The responsible player needs " + price + " Dabloons for this invitation.")
                    .build();
        }

        int remaining = MoneyHelper.GetBalance(player);
        MoneyHelper.SendBalanceMessage(player, -price, "Purchased external player invitation");
        return PurchaseExternalPlayerInviteResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(remaining)
                .setMessage("External player invitation purchased for " + price + " Dabloons.")
                .build();
    }
    private static final int MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 150;
    private static final int NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 250;
}
