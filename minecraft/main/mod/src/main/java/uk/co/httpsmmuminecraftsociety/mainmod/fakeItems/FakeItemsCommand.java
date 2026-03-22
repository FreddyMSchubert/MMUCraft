package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.ConsumableItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.DyeableItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.ItemFeature;

import java.util.Comparator;
import java.util.function.Predicate;

public final class FakeItemsCommand {
    private FakeItemsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ALL_SUGGESTIONS =
            suggestionsFor(item -> true);

    private static final SuggestionProvider<CommandSourceStack> CHARM_SUGGESTIONS =
            suggestionsFor(item -> hasFeature(item, CharmItemFeature.class));

    private static final SuggestionProvider<CommandSourceStack> CONSUMABLE_SUGGESTIONS =
            suggestionsFor(item -> hasFeature(item, ConsumableItemFeature.class));

    private static final SuggestionProvider<CommandSourceStack> DYEABLE_SUGGESTIONS =
            suggestionsFor(item -> hasFeature(item, DyeableItemFeature.class));

    private static final SuggestionProvider<CommandSourceStack> EQUIPPABLE_CHARM_SUGGESTIONS =
            suggestionsFor(item -> hasFeature(item, EquippableCharmItemFeature.class));

    private static final SuggestionProvider<CommandSourceStack> EQUIPPABLE_COSMETIC_SUGGESTIONS =
            suggestionsFor(item -> hasFeature(item, EquippableCosmeticItemFeature.class));

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fakeitems")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(buildCategoryCommand("all", ALL_SUGGESTIONS, item -> true))
                        .then(buildCategoryCommand(
                                "charm",
                                CHARM_SUGGESTIONS,
                                item -> hasFeature(item, CharmItemFeature.class)
                        ))
                        .then(buildCategoryCommand(
                                "consumable",
                                CONSUMABLE_SUGGESTIONS,
                                item -> hasFeature(item, ConsumableItemFeature.class)
                        ))
                        .then(buildCategoryCommand(
                                "dyeable",
                                DYEABLE_SUGGESTIONS,
                                item -> hasFeature(item, DyeableItemFeature.class)
                        ))
                        .then(buildCategoryCommand(
                                "equippable_charm",
                                EQUIPPABLE_CHARM_SUGGESTIONS,
                                item -> hasFeature(item, EquippableCharmItemFeature.class)
                        ))
                        .then(buildCategoryCommand(
                                "equippable_cosmetic",
                                EQUIPPABLE_COSMETIC_SUGGESTIONS,
                                item -> hasFeature(item, EquippableCosmeticItemFeature.class)
                        ))
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

    private static SuggestionProvider<CommandSourceStack> suggestionsFor(Predicate<FakeItem> filter) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(
                FakeItems.ALL.stream()
                        .filter(filter)
                        .sorted(Comparator.comparing(FakeItem::id))
                        .map(FakeItem::id),
                builder
        );
    }

    private static boolean hasFeature(FakeItem item, Class<? extends ItemFeature> featureClass) {
        return item.features().stream().anyMatch(featureClass::isInstance);
    }

    private static int give(
            CommandSourceStack source,
            String id,
            int amount,
            Predicate<FakeItem> filter
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FakeItem item = FakeItems.ID_MAP.get(id);

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
                () -> Component.literal("Gave " + amount + "x " + item.id()),
                true
        );

        return 1;
    }
}
