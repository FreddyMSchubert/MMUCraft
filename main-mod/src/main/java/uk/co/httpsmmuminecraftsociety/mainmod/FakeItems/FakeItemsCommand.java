package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.BasicFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CharmFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CosmeticFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.FakeItem;

import java.util.function.Predicate;

public final class FakeItemsCommand {
    private FakeItemsCommand() {}

    private static final String HAT_PREFIX = "cosmetic-hat-";
    private static final String CHARM_PREFIX = "cosmetic-charm-";
    private static final String COIN_PREFIX = "coin-";

    private static SuggestionProvider<CommandSourceStack> suggestionsFor(Predicate<FakeItem> filter) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(
                FakeItems.ALL.stream()
                        .filter(filter)
                        .map(FakeItem::getModelId),
                builder
        );
    }

    private static SuggestionProvider<CommandSourceStack> suggestionsForStripped(Predicate<FakeItem> filter, String prefix) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(
                FakeItems.ALL.stream()
                        .filter(filter)
                        .map(FakeItem::getModelId)
                        .filter(id -> id.startsWith(prefix))
                        .map(id -> id.substring(prefix.length())),
                builder
        );
    }

    private static final SuggestionProvider<CommandSourceStack> ALL_SUGGESTIONS =
            suggestionsFor(item -> true);

    private static final SuggestionProvider<CommandSourceStack> CHARM_SUGGESTIONS =
            suggestionsForStripped(
                    item -> item instanceof CharmFakeItem && item.getModelId().startsWith(CHARM_PREFIX),
                    CHARM_PREFIX
            );

    private static final SuggestionProvider<CommandSourceStack> HAT_SUGGESTIONS =
            suggestionsForStripped(
                    item -> item instanceof CosmeticFakeItem && item.getModelId().startsWith(HAT_PREFIX),
                    HAT_PREFIX
            );

    private static final SuggestionProvider<CommandSourceStack> COIN_SUGGESTIONS =
            suggestionsForStripped(
                    item -> item instanceof BasicFakeItem && item.getModelId().startsWith(COIN_PREFIX),
                    COIN_PREFIX
            );

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fakeitems")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(buildPrefixedCategoryCommand(
                                "charm",
                                CHARM_SUGGESTIONS,
                                item -> item instanceof CharmFakeItem && item.getModelId().startsWith(CHARM_PREFIX),
                                CHARM_PREFIX
                        ))
                        .then(buildPrefixedCategoryCommand(
                                "hat",
                                HAT_SUGGESTIONS,
                                item -> item instanceof CosmeticFakeItem && item.getModelId().startsWith(HAT_PREFIX),
                                HAT_PREFIX
                        ))
                        .then(buildPrefixedCategoryCommand(
                                "coin",
                                COIN_SUGGESTIONS,
                                item -> item instanceof BasicFakeItem && item.getModelId().startsWith(COIN_PREFIX),
                                COIN_PREFIX
                        ))
                        .then(buildCategoryCommand("all", ALL_SUGGESTIONS, item -> true))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCategoryCommand(
            String name,
            SuggestionProvider<CommandSourceStack> suggestions,
            Predicate<FakeItem> filter
    ) {
        return Commands.literal(name)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(suggestions)
                        .executes(ctx -> give(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                1,
                                filter
                        ))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> give(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                        filter
                                ))
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPrefixedCategoryCommand(
            String name,
            SuggestionProvider<CommandSourceStack> suggestions,
            Predicate<FakeItem> filter,
            String prefix
    ) {
        return Commands.literal(name)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(suggestions)
                        .executes(ctx -> give(
                                ctx.getSource(),
                                prefix + StringArgumentType.getString(ctx, "id"),
                                1,
                                filter
                        ))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> give(
                                        ctx.getSource(),
                                        prefix + StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                        filter
                                ))
                        )
                );
    }

    private static int give(CommandSourceStack source, String id, int amount, Predicate<FakeItem> filter) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FakeItem item = FakeItems.MODEL_ID_MAP.get(id);

        if (item == null || !filter.test(item)) {
            source.sendFailure(Component.literal("Unknown or invalid fake item id: " + id));
            return 0;
        }

        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = item.createItemStack();
            int max = stack.getMaxStackSize();
            int giveNow = Math.min(remaining, max);

            stack.setCount(giveNow);

            boolean inserted = player.getInventory().add(stack);
            if (!inserted) {
                player.drop(stack, false);
            }

            remaining -= giveNow;
        }

        source.sendSuccess(
                () -> Component.literal("Gave " + amount + "x " + item.getModelId()),
                true
        );

        return 1;
    }
}
