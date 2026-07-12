package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.UnlockBookLoot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopUnlockBookCharm implements Charm, UseCallbackCharm {
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private final String unlockType;
    private final String sourceItemId;
    private final String studyingMessage;
    private final String busyMessage;
    private final String failureMessage;

    private ShopUnlockBookCharm(
            String unlockType,
            String sourceItemId,
            String studyingMessage,
            String busyMessage,
            String failureMessage
    ) {
        this.unlockType = unlockType;
        this.sourceItemId = sourceItemId;
        this.studyingMessage = studyingMessage;
        this.busyMessage = busyMessage;
        this.failureMessage = failureMessage;
    }

    public static ShopUnlockBookCharm magicBook() {
        return new ShopUnlockBookCharm(
                "charm",
                "charm-magic-book",
                "Reading the magic book...",
                "Charm unlocking is already in progress. Try again in a moment.",
                "A charm could not be unlocked right now."
        );
    }

    public static ShopUnlockBookCharm fashionBook() {
        return new ShopUnlockBookCharm(
                "cosmetic",
                "charm-fashion-book",
                "Reading the fashion book...",
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

        player.sendSystemMessage(Component.literal(studyingMessage));

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
            player.sendSystemMessage(Component.literal(response.getMessage()));

            if (response.getUnlocked() && !player.isCreative()) {
                stack.shrink(1);
            }
        }));

        return InteractionResult.SUCCESS;
    }
}
