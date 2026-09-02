package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.WebsiteCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.UnlockBookLoot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopUnlockBookCharm implements Charm, UseCallbackCharm {
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private final String unlockType;
    private final String sourceItemId;
    private final String busyMessage;
    private final String failureMessage;

    private ShopUnlockBookCharm(
            String unlockType,
            String sourceItemId,
            String busyMessage,
            String failureMessage
    ) {
        this.unlockType = unlockType;
        this.sourceItemId = sourceItemId;
        this.busyMessage = busyMessage;
        this.failureMessage = failureMessage;
    }

    public static ShopUnlockBookCharm magicBook() {
        return new ShopUnlockBookCharm(
                "charm",
                "charm-magic-book",
                "Charm unlocking is already in progress. Try again in a moment.",
                "A charm could not be unlocked right now."
        );
    }

    public static ShopUnlockBookCharm fashionBook() {
        return new ShopUnlockBookCharm(
                "cosmetic",
                "charm-fashion-book",
                "Cosmetic unlocking is already in progress. Try again in a moment.",
                "A cosmetic could not be unlocked right now."
        );
    }

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        UUID playerId = player.getUUID();
        String inFlightKey = playerId + ":" + unlockType;

        if (!IN_FLIGHT.add(inFlightKey)) {
            player.sendSystemMessage(Component.literal(busyMessage));
            return InteractionResult.SUCCESS;
        }

        GameplayGrpcService.unlockNext(
                player.getGameProfile().name(),
                player.getUUID().toString(),
                sourceItemId,
                unlockType
        ).whenComplete((response, error) -> player.level().getServer().execute(() -> {
            IN_FLIGHT.remove(inFlightKey);

            if (error != null) {
                player.sendSystemMessage(Component.literal(failureMessage));
                return;
            }

            UnlockBookLoot.updateAvailability(player, response);
            Component message = Component.literal(response.getMessage());
            if (response.getUnlocked()) {
                message = message.copy()
                        .append(Component.literal(" "))
                        .append(WebsiteCommand.takeMeThere(
                                "charm".equals(unlockType)
                                        ? "charms"
                                        : "shop/" + response.getUnlockedId(),
                                "charm".equals(unlockType) ? "Upgrade it here." : "Get it here!",
                                "charm".equals(unlockType) ? ChatFormatting.DARK_PURPLE : ChatFormatting.YELLOW
                        ));
                if ("charm".equals(unlockType)) {
                    message = message.copy()
                            .append(Component.literal(" "))
                            .append(WebsiteCommand.takeMeThere(
                                    "shop/" + response.getUnlockedId(),
                                    "All details.",
                                    ChatFormatting.YELLOW
                            ));
                }
            }
            player.sendSystemMessage(message);

            if (response.getUnlocked()) {
                FakeItem unlockedItem = FakeItems.ID_MAP.get(response.getUnlockedId());
                ItemStack animationItem = unlockedItem != null ? unlockedItem.createItemStack() : stack;
                UnlockBookAnimation.play(player, animationItem);

                if ("charm".equals(unlockType) && unlockedItem != null) {
                    CharmItemFeature charm = unlockedItem.getFeature(CharmItemFeature.class);
                    if (charm != null) {
                        replaceUsedBook(player, stack, unlockedItem.createItemStackAtLevel(charm.minLevel()));
                    }
                } else if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
        }));

        return InteractionResult.SUCCESS;
    }

    private static void replaceUsedBook(ServerPlayer player, ItemStack book, ItemStack charm) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == book) {
                player.getInventory().setItem(slot, charm);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return;
            }
        }

        book.shrink(1);
        player.getInventory().add(charm);
        if (!charm.isEmpty()) {
            player.drop(charm, false);
        }
    }
}
