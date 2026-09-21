package uk.co.httpsmmuminecraftsociety.mainmod.money;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class MoneyCommand {
    private MoneyCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dabloons")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("get")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> get(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")
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
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> operation.run(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        IntegerArgumentType.getInteger(ctx, "amount")
                                ))
                        )
                );
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(
                () -> Component.literal(player.getName().getString() + " has ")
                        .append(MoneyHelper.FormatDabloonWord(MoneyHelper.GetBalance(player)))
                        .append(Component.literal(".")),
                false
        );
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, int amount) {
        int previousBalance = MoneyHelper.GetBalance(player);
        if (!MoneyHelper.SetMoney(player, amount)) {
            source.sendFailure(MoneyHelper.ReplaceDabloonWords(
                    "Could not set Dabloons for " + player.getName().getString() + "."
            ));
            return 0;
        }

        MoneyHelper.SendBalanceMessage(player, amount - previousBalance, "Balance set");
        sendChanged(source, "Set", player, amount);
        return 1;
    }

    private static int add(CommandSourceStack source, ServerPlayer player, int amount) {
        if (!MoneyHelper.GainMoney(player, amount)) {
            source.sendFailure(MoneyHelper.ReplaceDabloonWords(
                    "Could not add Dabloons for " + player.getName().getString() + "."
            ));
            return 0;
        }

        MoneyHelper.SendBalanceMessage(player, amount, "Received admin grant");
        sendChanged(source, "Added", player, amount);
        return 1;
    }

    private static int subtract(CommandSourceStack source, ServerPlayer player, int amount) {
        if (!MoneyHelper.ReduceMoney(player, amount)) {
            source.sendFailure(MoneyHelper.ReplaceDabloonWords(
                    player.getName().getString() + " does not have enough Dabloons."
            ));
            return 0;
        }

        MoneyHelper.SendBalanceMessage(player, -amount, "Admin adjustment");
        sendChanged(source, "Subtracted", player, amount);
        return 1;
    }

    private static void sendChanged(CommandSourceStack source, String action, ServerPlayer player, int amount) {
        if (source.getEntity() == player) return;
        source.sendSuccess(
                () -> Component.literal(action + " ")
                        .append(MoneyHelper.FormatDabloons(amount))
                        .append(Component.literal(" for " + player.getName().getString() + ".")),
                true
        );
    }

    @FunctionalInterface
    private interface MoneyOperation {
        int run(CommandSourceStack source, ServerPlayer player, int amount) throws CommandSyntaxException;
    }
}
