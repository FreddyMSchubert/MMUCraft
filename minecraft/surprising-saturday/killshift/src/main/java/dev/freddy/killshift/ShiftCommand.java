package dev.freddy.killshift;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

final class ShiftCommand {
    private ShiftCommand() { }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("shift")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("first", StringArgumentType.word())
                                .suggests((context, suggestions) -> suggest(context.getSource(), suggestions))
                                .executes(context -> shift(context.getSource(), null,
                                        StringArgumentType.getString(context, "first")))
                                .then(Commands.argument("second", StringArgumentType.word())
                                        .suggests((context, suggestions) -> suggest(context.getSource(), suggestions))
                                        .executes(context -> shift(context.getSource(),
                                                StringArgumentType.getString(context, "first"),
                                                StringArgumentType.getString(context, "second")))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder suggestions
    ) {
        source.getOnlinePlayerNames().forEach(suggestions::suggest);
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (Mob.class.isAssignableFrom(type.getBaseClass()) && MobRegistry.supports(type)) {
                suggestions.suggest(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
            }
        }
        return suggestions.buildFuture();
    }

    private static int shift(CommandSourceStack source, String targetName, String formName) {
        ServerPlayer target = targetName == null ? source.getPlayer()
                : source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal(targetName == null
                    ? "A player must run the one-argument form of /shift."
                    : "Target player is not online: " + targetName));
            return 0;
        }

        ServerPlayer sourcePlayer = source.getServer().getPlayerList().getPlayerByName(formName);
        if (sourcePlayer != null) {
            ShapeManager.shiftTo(target, sourcePlayer);
        } else {
            Identifier id = Identifier.tryParse(formName);
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || !MobRegistry.supports(type)
                    || !(type.create(target.level(), EntitySpawnReason.COMMAND) instanceof Mob mob)) {
                source.sendFailure(Component.literal("No online player or supported mob: " + formName));
                return 0;
            }
            ShapeManager.shiftTo(target, mob);
        }
        source.sendSuccess(() -> Component.literal("Shifted " + target.getScoreboardName() + " into " + formName + "."), false);
        if (source.getPlayer() != target) {
            target.sendSystemMessage(Component.literal("An admin shifted you into " + formName + "."));
        }
        return 1;
    }
}
