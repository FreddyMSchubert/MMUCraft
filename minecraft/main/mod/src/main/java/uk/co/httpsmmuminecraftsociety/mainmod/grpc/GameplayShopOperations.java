package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
final class GameplayShopOperations {
    private GameplayShopOperations() {}

    static PurchaseShopItemResponse purchaseShopItemOnMainThread(PurchaseShopItemRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setBalanceDabloons(0)
                    .setMessage("You have to be online on the server to buy from the shop.")
                    .build();
        }

        int price = Math.max(0, request.getPriceDabloons());
        int balance = MoneyHelper.GetBalance(player);
        if (balance < price) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("You need " + price + " dabloons, but only have " + balance + ".")
                    .build();
        }

        ItemStack grantStack = createShopGrantStack(request);
        if (grantStack == null) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("That shop item is not available on the server.")
                    .build();
        }

        if (!MoneyHelper.ReduceMoney(player, price)) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Could not take the dabloons for this purchase.")
                    .build();
        }

        if (!grantStack.isEmpty()) {
            player.getInventory().add(grantStack);
            if (!grantStack.isEmpty()) {
                player.drop(grantStack, false);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }

        int remaining = MoneyHelper.GetBalance(player);
        MoneyHelper.SendBalanceMessage(player,
                "Purchased " + request.getItemId() + " for " + price + " dabloons.");
        DiscordBridge.playerEvent("shop", player,
                "bought the " + request.getRarity() + " " + request.getDisplayName() + " "
                        + request.getItemType() + " from the shop for " + price + " dabloons.");
        return PurchaseShopItemResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(remaining)
                .setMessage("Purchased " + request.getItemId() + " for " + price + " dabloons.")
                .build();
    }

    private static ItemStack createShopGrantStack(PurchaseShopItemRequest request) {
        String deliveryKind = request.getDeliveryKind();
        String itemId = request.getItemId();

        if ("fake_item".equals(deliveryKind)) {
            if (!FakeItems.isKnownFakeItem(itemId)) {
                return null;
            }

            return FakeItems.createFakeItemStack(itemId, 1);
        }

        if ("vanilla_item".equals(deliveryKind)) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item == Items.AIR && !"minecraft:air".equals(itemId)) {
                return null;
            }

            return new ItemStack(item, 1);
        }

        return null;
    }
}
