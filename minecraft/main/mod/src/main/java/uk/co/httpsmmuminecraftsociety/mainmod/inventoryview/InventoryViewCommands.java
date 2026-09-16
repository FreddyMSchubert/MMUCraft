package uk.co.httpsmmuminecraftsociety.mainmod.inventoryview;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Editable online/offline player inventories, inspired by InvView's fake-player
 * persistence approach: https://github.com/PotatoPresident/InvView (MIT).
 */
public final class InventoryViewCommands {
    private static final Map<UUID, OfflinePlayerSession> OFFLINE_PLAYERS = new HashMap<>();

    private InventoryViewCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> closeStaleMenus(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> closeStaleMenus(handler.player));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("viewinv")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                        .executes(context -> open(context, InventoryViewMenu.Kind.INVENTORY))));
        dispatcher.register(Commands.literal("viewendchest")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                        .executes(context -> open(context, InventoryViewMenu.Kind.ENDER_CHEST))));
    }

    private static int open(CommandContext<CommandSourceStack> context, InventoryViewMenu.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer viewer = context.getSource().getPlayerOrException();
        NameAndId targetIdentity = GameProfileArgument.getGameProfiles(context, "target").iterator().next();
        TargetPlayer target = resolveTarget(context.getSource().getServer(), targetIdentity);
        Component title = Component.literal(targetIdentity.name() + (kind == InventoryViewMenu.Kind.INVENTORY
                ? "'s Inventory"
                : "'s Ender Chest"));

        if (viewer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new InventoryViewMenu(containerId, inventory, target, kind),
                title
        )).isEmpty()) {
            target.release();
        }
        return 1;
    }

    private static TargetPlayer resolveTarget(MinecraftServer server, NameAndId identity) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(identity.id());
        if (onlinePlayer != null && !onlinePlayer.hasDisconnected()) {
            return new TargetPlayer(onlinePlayer, null);
        }

        OfflinePlayerSession session = OFFLINE_PLAYERS.computeIfAbsent(
                identity.id(),
                uuid -> OfflinePlayerSession.load(server, identity)
        );
        session.retain();
        return new TargetPlayer(session.player(), session);
    }

    private static void closeStaleMenus(ServerPlayer target) {
        OfflinePlayerSession session = OFFLINE_PLAYERS.get(target.getUUID());
        if (session != null) session.save();
        InventoryViewMenu.closeMenusFor(target.getUUID());
    }

    static final class TargetPlayer {
        private final ServerPlayer player;
        private final OfflinePlayerSession offlineSession;

        private TargetPlayer(ServerPlayer player, OfflinePlayerSession offlineSession) {
            this.player = player;
            this.offlineSession = offlineSession;
        }

        ServerPlayer player() {
            return player;
        }

        void changed() {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            if (offlineSession != null) offlineSession.save();
        }

        void release() {
            if (offlineSession == null) return;
            offlineSession.save();
            if (offlineSession.release()) OFFLINE_PLAYERS.remove(player.getUUID(), offlineSession);
        }
    }

    private static final class OfflinePlayerSession {
        private final MinecraftServer server;
        private final ServerPlayer player;
        private int references;

        private OfflinePlayerSession(MinecraftServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
        }

        static OfflinePlayerSession load(MinecraftServer server, NameAndId identity) {
            ServerPlayer player = new ServerPlayer(
                    server,
                    server.overworld(),
                    new GameProfile(identity.id(), identity.name()),
                    ClientInformation.createDefault()
            );
            server.getPlayerList().loadPlayerData(identity).ifPresent(data -> {
                try (ProblemReporter.ScopedCollector problems =
                             new ProblemReporter.ScopedCollector(LogUtils.getLogger())) {
                    ValueInput input = TagValueInput.create(problems, server.registryAccess(), data);
                    player.load(input);
                    input.getString("Dimension")
                            .map(Identifier::tryParse)
                            .map(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)))
                            .ifPresent(player::setServerLevel);
                }
            });
            return new OfflinePlayerSession(server, player);
        }

        ServerPlayer player() {
            return player;
        }

        void retain() {
            references++;
        }

        boolean release() {
            references--;
            return references == 0;
        }

        void save() {
            PlayerDataPersistence.save(server, player);
        }
    }
}
