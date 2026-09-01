package uk.co.httpsmmuminecraftsociety.mainmod.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoneyHelper {
    public static final int DABLOON_CODEPOINT = 0xF0DAB;
    private static final String DABLOON_SYMBOL = Character.toString(DABLOON_CODEPOINT);
    private static final Pattern DABLOON_WORD = Pattern.compile("(?i)\\bdabloons?\\b");

    private MoneyHelper() {}

    public static MutableComponent FormatDabloons(int amount) {
        return Component.literal(formatNumber(amount) + " " + DABLOON_SYMBOL);
    }

    public static MutableComponent FormatDabloonWord(int amount) {
        return FormatDabloons(amount)
                .append(Component.literal(amount == 1 ? "abloon" : "abloons"));
    }

    public static MutableComponent FormatDabloonDelta(int amount) {
        ChatFormatting colour = amount < 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN;
        String sign = amount < 0 ? "-" : "+";
        return Component.literal("(" + sign)
                .append(FormatDabloons(Math.abs(amount)))
                .append(Component.literal(")"))
                .withStyle(colour);
    }

    public static MutableComponent ReplaceDabloonWords(String text) {
        Matcher matcher = DABLOON_WORD.matcher(text);
        MutableComponent result = Component.empty();
        int cursor = 0;

        while (matcher.find()) {
            result.append(Component.literal(text.substring(cursor, matcher.start())));
            result.append(Component.literal(DABLOON_SYMBOL + matcher.group().substring(1)));
            cursor = matcher.end();
        }

        return result.append(Component.literal(text.substring(cursor)));
    }

    public static int GetBalance(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) {
            return 0;
        }

        int balance = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            int walletBalance = WalletCharm.isWallet(stack);
            if (walletBalance >= 0) {
                balance += walletBalance;
                continue;
            }

            int coinValue = WalletCharm.isCoin(stack);
            if (coinValue > 0) {
                balance += coinValue * stack.getCount();
            }
        }

        return balance;
    }

    public static void SendBalanceMessage(ServerPlayer player, int delta, String message) {
        SendBalanceMessage(player, delta, ReplaceDabloonWords(message));
    }

    public static void SendBalanceMessage(ServerPlayer player, int delta, Component message) {
        player.sendSystemMessage(Component.literal("{")
                .withStyle(ChatFormatting.GREEN)
                .append(FormatDabloons(GetBalance(player)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("} ").withStyle(ChatFormatting.GREEN))
                .append(message.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" "))
                .append(FormatDabloonDelta(delta)));
    }

    private static String formatNumber(int amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    public static boolean ReduceMoney(ServerPlayer player, int amount) {
        if (player == null || player.hasDisconnected() || amount < 0) {
            return false;
        }

        int balance = GetBalance(player);
        if (balance < amount) {
            return false;
        }

        replaceMoney(player, balance - amount);
        return true;
    }

    public static boolean GainMoney(ServerPlayer player, int amount) {
        if (player == null || player.hasDisconnected() || amount < 0) {
            return false;
        }

        replaceMoney(player, GetBalance(player) + amount);
        return true;
    }

    public static boolean SetMoney(ServerPlayer player, int amount) {
        if (player == null || player.hasDisconnected() || amount < 0) {
            return false;
        }

        replaceMoney(player, amount);
        return true;
    }

    private static void replaceMoney(ServerPlayer player, int amount) {
        Inventory inventory = player.getInventory();
        List<ItemStack> wallets = collectWalletsAndClearCoins(inventory);

        if (!wallets.isEmpty()) {
            ItemStack firstWallet = wallets.getFirst();
            WalletCharm.setBalance(firstWallet, amount, false);
            for (int i = 1; i < wallets.size(); i++) {
                WalletCharm.setBalance(wallets.get(i), 0, false);
            }
        } else {
            addCoins(player, amount);
        }

        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<ItemStack> collectWalletsAndClearCoins(Inventory inventory) {
        List<ItemStack> wallets = new ArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (WalletCharm.isWallet(stack) >= 0) {
                wallets.add(stack);
            } else if (WalletCharm.isCoin(stack) > 0) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        return wallets;
    }

    public static List<ItemStack> createCoinStacks(int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        for (WalletCharm.CoinDef coin : WalletCharm.COINS.stream()
                .sorted(Comparator.comparingInt(WalletCharm.CoinDef::value).reversed())
                .toList()) {
            int count = amount / coin.value();
            amount %= coin.value();

            while (count > 0) {
                int stackSize = Math.min(count, 64);
                stacks.add(FakeItems.createFakeItemStack(coin.id(), stackSize));
                count -= stackSize;
            }
        }
        return stacks;
    }

    private static void addCoins(ServerPlayer player, int amount) {
        for (ItemStack stack : createCoinStacks(amount)) {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) player.drop(stack, false);
        }
    }
}
