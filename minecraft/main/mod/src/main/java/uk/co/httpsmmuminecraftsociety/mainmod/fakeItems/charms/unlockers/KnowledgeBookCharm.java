package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.WebsiteCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.UnlockBookLoot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KnowledgeBookCharm implements Charm, UseCallbackCharm {
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, net.minecraft.server.level.ServerLevel level, int charmLevel) {
        UUID playerId = player.getUUID();

        if (!IN_FLIGHT.add(playerId)) {
            player.sendSystemMessage(Component.literal("Knowledge is already being unlocked. Try again in a moment."));
            return InteractionResult.SUCCESS;
        }

        GameplayGrpcService.unlockNext(
                player.getGameProfile().name(),
                player.getUUID().toString(),
                "charm-knowledge-book",
                "knowledge"
        ).whenComplete((response, error) -> player.level().getServer().execute(() -> {
            IN_FLIGHT.remove(playerId);

            if (error != null)
            {
                player.sendSystemMessage(Component.literal("Knowledge could not be unlocked right now."));
                return;
            }

            UnlockBookLoot.updateAvailability(player, response);
            Component message = Component.literal(response.getMessage());
            if (response.getUnlocked()) {
                message = message.copy()
                        .append(Component.literal(" "))
                        .append(WebsiteCommand.takeMeThere("play/knowledge/" + response.getKnowledgeId(), "Click to read!", ChatFormatting.RED));
            }
            player.sendSystemMessage(message);

            if (response.getUnlocked()) {
                UnlockBookAnimation.play(player, stack);

                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
        }));

        return InteractionResult.SUCCESS;
    }
}
