package uk.co.httpsmmuminecraftsociety.mainmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.CommandNodeAccessor;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class PlayerCommandWhitelist {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("tell", "tip", "website");

    private PlayerCommandWhitelist() {}

    public static void apply(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        makeTellIndependent(dispatcher);

        for (CommandNode<CommandSourceStack> command : dispatcher.getRoot().getChildren()) {
            Predicate<CommandSourceStack> originalRequirement = command.getRequirement();
            boolean allowed = ALLOWED_COMMANDS.contains(command.getName());
            ((CommandNodeAccessor<CommandSourceStack>) command).mainmod$setRequirement(
                    source -> originalRequirement.test(source) && (allowed || isOperator(source))
            );
        }
    }

    private static boolean isOperator(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static void makeTellIndependent(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> tell = dispatcher.getRoot().getChild("tell");
        CommandNode<CommandSourceStack> msg = dispatcher.getRoot().getChild("msg");
        if (tell == null || msg == null || tell.getRedirect() != msg) return;

        for (CommandNode<CommandSourceStack> child : List.copyOf(msg.getChildren())) tell.addChild(child);
        ((CommandNodeAccessor<CommandSourceStack>) tell).mainmod$setRedirect(null);
    }
}
