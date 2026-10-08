package uk.co.httpsmmuminecraftsociety.mainmod.money;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemContainerContents;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.RecordDabloonInteractionRequest;
import java.util.function.Supplier;

public final class DabloonInteractionLog {
    private static final ThreadLocal<ServerPlayer> DROP_ACTOR = new ThreadLocal<>();
    private DabloonInteractionLog() {}

    public static String kind(ItemStack stack) {
        if (WalletCharm.isWallet(stack) >= 0) return "wallet";
        if (WalletCharm.isCoin(stack) > 0) return "coin";
        return summarize(stack).found ? "container" : "";
    }

    public static int value(ItemStack stack) {
        return (int) Math.min(Integer.MAX_VALUE, summarize(stack).value);
    }

    public static String describe(ItemStack stack) {
        return summarize(stack).detail.toString();
    }

    private static CurrencySummary summarize(ItemStack stack) {
        CurrencySummary summary = new CurrencySummary();
        scan(stack, "", 1, 0, summary);
        assert !summary.found || !summary.detail.isEmpty() : "Currency detail missing";
        if (summary.truncated) summary.detail.append(" [truncated]");
        return summary;
    }

    private static void scan(ItemStack stack, String path, long multiplier, int depth, CurrencySummary summary) {
        if (stack.isEmpty()) return;
        // ponytail: Limit scans to 32 levels and 8,192 stacks. Raise this limit if legal containers grow.
        if (depth > 32 || ++summary.visited > 8192) {
            summary.truncated = true;
            return;
        }
        String id = itemId(stack);
        String currentPath = path.isEmpty() ? id : path + "/" + id;
        long count = Math.min(Integer.MAX_VALUE, multiplier * stack.getCount());
        int walletBalance = WalletCharm.isWallet(stack);
        int coinValue = WalletCharm.isCoin(stack);
        if (walletBalance >= 0 || coinValue > 0) {
            summary.found = true;
            long value = count * (walletBalance >= 0 ? walletBalance : coinValue);
            summary.value = Math.min(Integer.MAX_VALUE, summary.value + value);
            if (summary.detail.length() < 3000) {
                if (!summary.detail.isEmpty()) summary.detail.append(", ");
                summary.detail.append(currentPath).append(" x").append(count).append(" value=").append(value);
            } else summary.truncated = true;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (container != null) {
            int slot = 0;
            for (ItemStack child : container.itemCopies().toList()) {
                scan(child, currentPath + "[slot=" + slot++ + "]", count, depth + 1, summary);
            }
        }
        if (bundle != null) {
            int slot = 0;
            for (ItemStack child : bundle.itemCopies().toList()) {
                scan(child, currentPath + "[bundle=" + slot++ + "]", count, depth + 1, summary);
            }
        }
    }

    private static final class CurrencySummary {
        private boolean found;
        private boolean truncated;
        private int visited;
        private long value;
        private final StringBuilder detail = new StringBuilder();
    }

    public static ServerPlayer currentDropActor() {
        return DROP_ACTOR.get();
    }

    public static ItemEntity drop(Player player, ItemStack stack) {
        return withActor(player, () -> player.drop(stack, false, Prediction.SERVER_ONLY));
    }

    public static <T> T withActor(Player player, Supplier<T> action) {
        ServerPlayer previous = DROP_ACTOR.get();
        if (player instanceof ServerPlayer serverPlayer) DROP_ACTOR.set(serverPlayer);
        try {
            return action.get();
        } finally {
            if (previous == null) DROP_ACTOR.remove();
            else DROP_ACTOR.set(previous);
        }
    }

    public static String itemId(ItemStack stack) {
        CustomModelData model = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        return model.strings().isEmpty()
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                : model.strings().getFirst();
    }

    public static void record(ServerPlayer player, String action, String kind, String itemId,
                              int count, int amount, Integer before, Integer after, String context) {
        RecordDabloonInteractionRequest.Builder request = RecordDabloonInteractionRequest.newBuilder()
                .setAction(action)
                .setItemKind(kind)
                .setItemId(itemId)
                .setItemCount(Math.max(0, count))
                .setAmountDabloons(Math.max(0, amount))
                .setActorName(player.getName().getString())
                .setMinecraftUuid(player.getUUID().toString())
                .setContext(context)
                .setDimension(player.level().dimension().identifier().toString())
                .setX(player.blockPosition().getX())
                .setY(player.blockPosition().getY())
                .setZ(player.blockPosition().getZ())
                .setUnixMs(System.currentTimeMillis());
        if (before != null) request.setBalanceBefore(before);
        if (after != null) request.setBalanceAfter(after);
        send(request.build());
    }

    public static void recordWorld(String action, ItemStack stack, String dimension,
                                   int x, int y, int z, String context) {
        recordWorld(action, kind(stack), itemId(stack), stack.getCount(), value(stack), dimension, x, y, z, context);
    }

    public static void recordWorld(String action, String kind, String itemId, int count, int amount,
                                   String dimension, int x, int y, int z, String context) {
        send(RecordDabloonInteractionRequest.newBuilder()
                .setAction(action)
                .setItemKind(kind)
                .setItemId(itemId)
                .setItemCount(count)
                .setAmountDabloons(amount)
                .setActorName("World")
                .setContext(context)
                .setDimension(dimension)
                .setX(x).setY(y).setZ(z)
                .setUnixMs(System.currentTimeMillis())
                .build());
    }

    private static void send(RecordDabloonInteractionRequest request) {
        GameplayGrpcService.recordDabloonInteraction(request).exceptionally(error -> {
            MainMod.LOGGER.warn("Could not record Dabloon interaction {}: {}", request.getAction(), error.toString());
            return null;
        });
    }
}
