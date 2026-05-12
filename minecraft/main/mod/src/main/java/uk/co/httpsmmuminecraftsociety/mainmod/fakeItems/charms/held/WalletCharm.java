package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WalletCharm implements Charm
{
    public static final String BALANCE_ID = "wallet_balance";

    public record CoinDef(String id, int value) {}
    public static final List<CoinDef> COINS = List.of(
            new CoinDef("coin-1000000", 1_000_000),
            new CoinDef("coin-500000",   500_000),
            new CoinDef("coin-100000",   100_000),
            new CoinDef("coin-50000",     50_000),
            new CoinDef("coin-10000",     10_000),
            new CoinDef("coin-5000",       5_000),
            new CoinDef("coin-1000",       1_000),
            new CoinDef("coin-500",          500),
            new CoinDef("coin-100",          100),
            new CoinDef("coin-50",            50),
            new CoinDef("coin-10",            10),
            new CoinDef("coin-5",              5),
            new CoinDef("coin-1",              1)
    );
    private static final Map<String, CoinDef> COINS_BY_ID = COINS.stream()
            .collect(Collectors.toUnmodifiableMap(CoinDef::id, c -> c));

    public static void setBalance(ItemStack wallet, int balanceDiff, boolean diff) {
        CompoundTag nbt = wallet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putInt(BALANCE_ID, (diff ? nbt.getIntOr(BALANCE_ID, 0) : 0) + balanceDiff);
        wallet.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        updateTooltip(wallet);
    }
    public static void addCoinToWallet(ItemStack wallet, ItemStack coin) {
        int walletBalance = isWallet(wallet);
        if (walletBalance == -1) return;
        int coinVal = isCoin(coin);
        if (coinVal == 0) return;

        walletBalance += coinVal * coin.count();
        setBalance(wallet, walletBalance, false);
    }
    // Tuple<RemovedCoinStacks, LighterWallet>
    public static ItemStack removeCoinsFromWallet(ItemStack wallet) {
        int walletBalance = isWallet(wallet);
        if (walletBalance <= 0) return ItemStack.EMPTY;

        for (CoinDef def : COINS) {
            if (def.value() > walletBalance) continue;
            int coinValue = def.value();
            int coinCount = walletBalance / def.value(); // purposeful int division round-down
            if (coinCount > 64) coinCount = 64;

            setBalance(wallet, - coinCount * coinValue, true);

            ItemStack returnCoins = FakeItems.ID_MAP.get(def.id()).createItemStack();
            returnCoins.setCount(coinCount);
            return returnCoins;
        }

        return ItemStack.EMPTY;
    }

    private static void updateTooltip(ItemStack wallet) {
        CompoundTag nbt = wallet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int balance = nbt.getIntOr(BALANCE_ID, 0);

        List<Component> lines = new ArrayList<>(wallet.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (!lines.isEmpty()) {
            lines.set(lines.size() - 1, Component.literal("Currently holds " + balance + (balance == 1 ? " dabloon." : " dabloons.")));
            wallet.set(DataComponents.LORE, new ItemLore(lines));
        }
    }

    // returns -1 if it isnt a wallet
    public static int isWallet(ItemStack stack)
    {
        if (stack.isEmpty()) return -1;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) return -1;
        String itemId = cmd.strings().getFirst();
        FakeItem fitem = FakeItems.ID_MAP.get(itemId);
        if (fitem == null) return -1;

        CharmItemFeature cif = fitem.getFeature(CharmItemFeature.class);
        if (cif == null) return -1;
        if (cif.charm().getClass() != WalletCharm.class) return -1;

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return nbt.getIntOr(BALANCE_ID, 0);
    }
    // returns 0 if it isnt a coin
    public static int isCoin(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) return 0;
        CoinDef coin = COINS_BY_ID.get(cmd.strings().getFirst());
        return coin == null ? 0 : coin.value();
    }

    // multiple coin insertion utils

    public static boolean isWalletInsertGrid(CraftingContainer input) {
        boolean foundWallet = false;
        boolean foundCoin = false;

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (WalletCharm.isWallet(stack) >= 0) {
                if (foundWallet) return false;
                foundWallet = true;
                continue;
            }

            if (WalletCharm.isCoin(stack) > 0) {
                foundCoin = true;
                continue;
            }

            return false;
        }

        return foundWallet && foundCoin;
    }
    public static boolean clearRemainingCoinStacks(CraftingContainer input) {
        boolean changed = false;

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (WalletCharm.isCoin(stack) > 0) {
                input.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }

        if (changed) {
            input.setChanged();
        }

        return changed;
    }
}
