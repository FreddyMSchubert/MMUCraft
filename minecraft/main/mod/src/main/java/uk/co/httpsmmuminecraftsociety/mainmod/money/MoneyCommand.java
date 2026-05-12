package uk.co.httpsmmuminecraftsociety.mainmod.money;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.stream.Stream;

public final class MoneyCommand {
    private MoneyCommand() {}

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("Only @s or a single online player username is allowed."));

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    Stream.concat(
                            Stream.of("@s"),
                            ctx.getSource().getOnlinePlayerNames().stream()
                    ),
                    builder
            );

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("money")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("get")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .executes(ctx -> get(
                                                ctx.getSource(),
                                                getTarget(ctx.getSource(), StringArgumentType.getString(ctx, "target"))
                                        ))
                                )
                        )
                        .then(amountCommand("set", MoneyCommand::set))
                        .then(amountCommand("add", MoneyCommand::add))
                        .then(amountCommand("subtract", MoneyCommand::subtract))
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> amountCommand(
            String name,
            MoneyOperation operation
    ) {
        return Commands.literal(name)
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> operation.run(
                                        ctx.getSource(),
                                        getTarget(ctx.getSource(), StringArgumentType.getString(ctx, "target")),
                                        IntegerArgumentType.getInteger(ctx, "amount")
                                ))
                        )
                );
    }

    private static ServerPlayer getTarget(CommandSourceStack source, String target) throws CommandSyntaxException {
        if (target.equals("@s")) {
            return source.getPlayerOrException();
        }

        if (target.startsWith("@")) {
            throw PLAYER_NOT_FOUND.create();
        }

        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(target);
        if (player == null || player.hasDisconnected()) {
            throw PLAYER_NOT_FOUND.create();
        }

        return player;
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(
                () -> Component.literal(player.getName().getString() + " has " + MoneyHelper.GetBalance(player) + " dabloons."),
                false
        );
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, int amount) {
        if (!MoneyHelper.SetMoney(player, amount)) {
            source.sendFailure(Component.literal("Could not set money for " + player.getName().getString() + "."));
            return 0;
        }

        sendChanged(source, "Set", player, amount);
        return 1;
    }

    private static int add(CommandSourceStack source, ServerPlayer player, int amount) {
        if (!MoneyHelper.GainMoney(player, amount)) {
            source.sendFailure(Component.literal("Could not add money for " + player.getName().getString() + "."));
            return 0;
        }

        sendChanged(source, "Added", player, amount);
        return 1;
    }

    private static int subtract(CommandSourceStack source, ServerPlayer player, int amount) {
        if (!MoneyHelper.ReduceMoney(player, amount)) {
            source.sendFailure(Component.literal(player.getName().getString() + " does not have enough dabloons."));
            return 0;
        }

        sendChanged(source, "Subtracted", player, amount);
        return 1;
    }

    private static void sendChanged(CommandSourceStack source, String action, ServerPlayer player, int amount) {
        source.sendSuccess(
                () -> Component.literal(action + " " + amount + " dabloons for " + player.getName().getString() + "."),
                true
        );
    }

    @FunctionalInterface
    private interface MoneyOperation {
        int run(CommandSourceStack source, ServerPlayer player, int amount) throws CommandSyntaxException;
    }
}
