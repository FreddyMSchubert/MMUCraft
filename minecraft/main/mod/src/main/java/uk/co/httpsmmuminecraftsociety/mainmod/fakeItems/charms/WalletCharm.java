package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    private record CoinDef(String id, int value) {}
    private static final List<CoinDef> COINS = List.of(
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

    private static void setBalance(ItemStack wallet, int balanceDiff, boolean diff) {
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

        walletBalance += coinVal;
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
}
