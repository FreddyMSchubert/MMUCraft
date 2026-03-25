package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WalletUtils
{
    public static final String WALLET_MODEL_ID = "wallet";

    private static final String MARKER_NBT_ID = "is_wallet";

    private record CoinDef(String id, int value) {}
    private static final List<CoinDef> COINS_DESC = List.of(
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
    private static final Map<String, CoinDef> COINS_BY_ID = COINS_DESC.stream()
            .collect(Collectors.toUnmodifiableMap(CoinDef::id, c -> c));

    public static void sortWallet(ItemStack bundleStack) {
        MainMod.LOGGER.info("Got the call to sort this wallet - checking whether it is one.");

        if (!isWallet(bundleStack)) return;

        MainMod.LOGGER.info("It is one! Hooray! Sorting time!");

        BundleContents contents = bundleStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        long dabloons = 0;
        for (ItemStackTemplate stack : contents.items()) {
            CoinDef coin = getCoinDef(stack);
            if (coin == null) {
                MainMod.LOGGER.info("found a non-coin stack in the wallet. weird. returning.");
                return;
            }
            dabloons += (long) coin.value() * stack.count();
        }

        List<ItemStackTemplate> newCoins = new ArrayList<>();
        long remaining = dabloons;

        for (CoinDef coin : COINS_DESC) {
            long count = remaining / coin.value();
            if (count <= 0) continue;

            while (count > 0) {
                ItemStack coinStack = FakeItems.ID_MAP.get(coin.id()).createItemStack();
                long take = Math.min(count, coinStack.getMaxStackSize());
                coinStack.setCount((int)take);
                newCoins.add(ItemStackTemplate.fromNonEmptyStack(coinStack));
                count -= take;
            }

            remaining %= coin.value();
        }

        bundleStack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(newCoins));

        List<Component> lines = new ArrayList<>(bundleStack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (!lines.isEmpty()) {
            lines.set(lines.size() - 1, Component.literal("Currently stored: " + dabloons + (dabloons == 1 ? " dabloon." : " dabloons.")));
            bundleStack.set(DataComponents.LORE, new ItemLore(lines));
        }
    }

    public static boolean isWallet(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BundleItem)) return false;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBooleanOr(MARKER_NBT_ID, false);
    }
    public static boolean isCoin(ItemStackTemplate stack)
    {
        return getCoinDef(stack) != null;
    }

    private static @Nullable CoinDef getCoinDef(ItemStackTemplate stack)
    {
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) return null;
        return COINS_BY_ID.get(cmd.strings().getFirst());
    }

    public static ItemStack createWalletStack()
    {
        ItemStack stack = new ItemStack(Items.BUNDLE, 1);

        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(WALLET_MODEL_ID), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Wallet"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Stores & compresses dabloons."),
                Component.literal("Currently stored: 0 dabloons.")
        )));
        stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
        stack.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(MARKER_NBT_ID, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return stack;
    }
}