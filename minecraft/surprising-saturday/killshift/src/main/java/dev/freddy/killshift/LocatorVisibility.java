package dev.freddy.killshift;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.gamerules.GameRules;

final class LocatorVisibility {
    private static final Identifier SEND_RANGE = Identifier.fromNamespaceAndPath(Killshift.MOD_ID, "locator_send");
    private static final Identifier SEND_MULTIPLIER = Identifier.fromNamespaceAndPath(Killshift.MOD_ID, "locator_send_multiplier");
    private static final Identifier RECEIVE_RANGE = Identifier.fromNamespaceAndPath(Killshift.MOD_ID, "locator_receive");
    private static final Identifier RECEIVE_MULTIPLIER = Identifier.fromNamespaceAndPath(Killshift.MOD_ID, "locator_receive_multiplier");

    private LocatorVisibility() { }

    static void tick(MinecraftServer server, ServerPlayer player) {
        if (!player.level().getGameRules().get(GameRules.LOCATOR_BAR)) {
            player.level().getGameRules().set(GameRules.LOCATOR_BAR, true, server);
        }
        keepRange(player.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE), SEND_RANGE, SEND_MULTIPLIER);
        keepRange(player.getAttribute(Attributes.WAYPOINT_RECEIVE_RANGE), RECEIVE_RANGE, RECEIVE_MULTIPLIER);
    }

    private static void keepRange(AttributeInstance range, Identifier additiveId, Identifier multiplierId) {
        if (range == null) return;
        if (!range.hasModifier(additiveId)) {
            range.addTransientModifier(new AttributeModifier(additiveId, 60_000_000.0,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        if (!range.hasModifier(multiplierId)) {
            range.addTransientModifier(new AttributeModifier(multiplierId, 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
