package uk.co.httpsmmuminecraftsociety.mainmod.miniblocks;

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

import java.util.Collection;

public final class MiniBlockCommand {
    private static final SuggestionProvider<CommandSourceStack> MINI_BLOCK_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    MiniBlockCatalog.definitions().stream().map(MiniBlockDefinition::id).sorted(),
                    builder
            );

    private MiniBlockCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("givemini")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(MINI_BLOCK_SUGGESTIONS)
                                        .executes(context -> give(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                StringArgumentType.getString(context, "id"),
                                                1
                                        ))
                                        .then(Commands.argument(
                                                        "amount",
                                                        IntegerArgumentType.integer(1, 1_000_000)
                                                )
                                                .executes(context -> give(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "amount")
                                                ))
                                        )
                                )
                        )
        );
    }

    private static int give(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String miniBlockId,
            int amount
    ) throws CommandSyntaxException {
        MiniBlockDefinition definition = MiniBlockCatalog.find(miniBlockId).orElse(null);
        if (definition == null) {
            source.sendFailure(Component.literal("Unknown mini block id: " + miniBlockId));
            return 0;
        }

        for (ServerPlayer target : targets) {
            giveToPlayer(target, definition, amount);
        }

        source.sendSuccess(() -> buildSuccessMessage(targets, definition, amount), true);
        return targets.size();
    }

    private static void giveToPlayer(ServerPlayer player, MiniBlockDefinition definition, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = definition.createOutput();
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
            MiniBlockDefinition definition,
            int amount
    ) {
        if (targets.size() == 1) {
            ServerPlayer target = targets.iterator().next();
            return Component.literal(
                    "Gave " + amount + "x " + definition.id() + " to " + target.getName().getString()
            );
        }

        return Component.literal(
                "Gave " + amount + "x " + definition.id() + " to " + targets.size() + " players"
        );
    }
}
