package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.FakeItem;

public final class FakeItemsCommand {
    private FakeItemsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ID_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    FakeItems.ALL.stream().map(FakeItem::getModelId),
                    builder
            );

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fakeitems")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))

                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(ID_SUGGESTIONS)
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        ))
                                )
                        )
        );
    }

    private static int give(CommandSourceStack source, String id, int amount) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();

        FakeItem d = FakeItems.MODEL_ID_MAP.get(id);

        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = d.createItemStack();
            int max = stack.getMaxStackSize();
            int giveNow = Math.min(remaining, max);

            stack.setCount(giveNow);

            boolean inserted = player.getInventory().add(stack);
            if (!inserted) {
                player.drop(stack, false);
            }

            remaining -= giveNow;
        }

        return 1;
    }
}
