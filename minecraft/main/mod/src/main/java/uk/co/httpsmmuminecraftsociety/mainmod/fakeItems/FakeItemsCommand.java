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
import java.util.List;
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
        return (ctx, builder) -> {
            String prefix = removablePrefix(filter);

            return SharedSuggestionProvider.suggest(
                    FakeItems.ALL.stream()
                            .filter(filter)
                            .map(item -> stripPrefix(item.id(), prefix))
                            .sorted(Comparator.naturalOrder()),
                    builder
            );
        };
    }

    private static boolean hasFeature(FakeItem item, Class<? extends ItemFeature> featureClass) {
        return item.features().stream().anyMatch(featureClass::isInstance);
    }

    private static int give(
            CommandSourceStack source,
            String selectedId,
            int amount,
            Predicate<FakeItem> filter
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FakeItem item = resolveItem(selectedId, filter);

        if (item == null) {
            source.sendFailure(Component.literal("Unknown or invalid fake item id: " + selectedId));
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

    private static FakeItem resolveItem(String selectedId, Predicate<FakeItem> filter) {
        FakeItem direct = FakeItems.ID_MAP.get(selectedId);
        if (direct != null && filter.test(direct)) {
            return direct;
        }

        String prefix = removablePrefix(filter);
        if (!prefix.isEmpty()) {
            FakeItem prefixed = FakeItems.ID_MAP.get(prefix + selectedId);
            if (prefixed != null && filter.test(prefixed)) {
                return prefixed;
            }
        }

        return null;
    }

    private static String removablePrefix(Predicate<FakeItem> filter) {
        List<String> ids = FakeItems.ALL.stream()
                .filter(filter)
                .map(FakeItem::id)
                .sorted()
                .toList();

        if (ids.size() < 2) {
            return "";
        }

        String prefix = ids.get(0);
        for (int i = 1; i < ids.size(); i++) {
            prefix = commonPrefix(prefix, ids.get(i));
            if (prefix.isEmpty()) {
                return "";
            }
        }

        prefix = trimToSeparator(prefix);

        if (prefix.isEmpty()) {
            return "";
        }

        for (String id : ids) {
            if (id.length() <= prefix.length()) {
                return "";
            }
        }

        return prefix;
    }

    private static String commonPrefix(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }

    private static String trimToSeparator(String prefix) {
        int cut = Math.max(
                Math.max(prefix.lastIndexOf('_'), prefix.lastIndexOf(':')),
                Math.max(
                        Math.max(prefix.lastIndexOf('/'), prefix.lastIndexOf('-')),
                        prefix.lastIndexOf('.')
                )
        );

        if (cut < 0) {
            return "";
        }

        return prefix.substring(0, cut + 1);
    }

    private static String stripPrefix(String id, String prefix) {
        if (!prefix.isEmpty() && id.startsWith(prefix)) {
            return id.substring(prefix.length());
        }
        return id;
    }
}
