package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.Collection;
import java.util.Comparator;

public final class FakeItemsCommand {
    private FakeItemsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> FAKE_ITEM_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    FakeItems.ALL.stream()
                            .map(FakeItem::id)
                            .sorted(Comparator.naturalOrder()),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> CHARM_LEVEL_SUGGESTIONS = (ctx, builder) -> {
        FakeItem item = FakeItems.ID_MAP.get(StringArgumentType.getString(ctx, "id"));
        CharmItemFeature charm = item == null ? null : item.getFeature(CharmItemFeature.class);
        if (charm == null) {
            return builder.buildFuture();
        }

        for (int level = charm.minLevel(); level <= charm.maxLevel(); level++) {
            builder.suggest(level);
        }
        return builder.buildFuture();
    };

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("givefake")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(FAKE_ITEM_SUGGESTIONS)
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "id"),
                                                1,
                                                null
                                        ))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                                .executes(ctx -> give(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        null
                                                ))
                                                .then(Commands.argument("charm_level", IntegerArgumentType.integer(0))
                                                        .suggests(CHARM_LEVEL_SUGGESTIONS)
                                                        .executes(ctx -> give(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "targets"),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "amount"),
                                                                IntegerArgumentType.getInteger(ctx, "charm_level")
                                                        ))
                                                )
                                        )
                                )
                        )
        );
    }

    private static int give(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String fakeItemId,
            int amount,
            Integer charmLevel
    ) throws CommandSyntaxException {
        FakeItem item = FakeItems.ID_MAP.get(fakeItemId);
        if (item == null) {
            source.sendFailure(Component.literal("Unknown fake item id: " + fakeItemId));
            return 0;
        }

        CharmItemFeature charmFeature = item.getFeature(CharmItemFeature.class);
        if (charmLevel != null && charmFeature == null) {
            source.sendFailure(Component.literal(
                    "Fake item " + item.id() + " is not a charm and cannot take a charm level."
            ));
            return 0;
        }

        if (charmLevel != null
                && (charmLevel < charmFeature.minLevel() || charmLevel > charmFeature.maxLevel())) {
            source.sendFailure(buildInvalidCharmLevelMessage(item, charmLevel, charmFeature));
            return 0;
        }

        for (ServerPlayer target : targets) {
            giveToPlayer(target, item, amount, charmLevel);
        }

        source.sendSuccess(() -> buildSuccessMessage(targets, item, amount, charmLevel), true);
        return targets.size();
    }

    private static void giveToPlayer(ServerPlayer player, FakeItem item, int amount, Integer charmLevel) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = charmLevel == null
                    ? item.createItemStack()
                    : item.createItemStackAtLevel(charmLevel);

            int giveNow = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(giveNow);

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }

            remaining -= giveNow;
        }
    }

    private static Component buildSuccessMessage(
            Collection<ServerPlayer> targets,
            FakeItem item,
            int amount,
            Integer charmLevel
    ) {
        String levelText = charmLevel == null ? "" : " at level " + charmLevel;
        if (targets.size() == 1) {
            ServerPlayer target = targets.iterator().next();
            return Component.literal(
                    "Gave " + amount + "x " + item.id() + levelText + " to " + target.getName().getString()
            );
        }

        return Component.literal(
                "Gave " + amount + "x " + item.id() + levelText + " to " + targets.size() + " players"
        );
    }

    private static Component buildInvalidCharmLevelMessage(FakeItem item, int level, CharmItemFeature feature) {
        return Component.literal(
                "Invalid level " + level + " for " + item.id()
                        + ". Allowed charm levels are " + feature.minLevel() + ".." + feature.maxLevel()
        );
    }
}
