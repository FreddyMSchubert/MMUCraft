package uk.co.httpsmmuminecraftsociety.mainmod.connection;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("playerauth");
    private static final long STATUS_CHECK_INTERVAL_MS = 3_000L;
    private static final double SPAWN_RADIUS_SQUARED = 9.0D;

    private final AuthApiClient apiClient = new AuthApiClient();
    private final Map<UUID, PlayerAuthState> playerStates = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;

    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("auth").executes(context -> {
                    Entity entity = context.getSource().getEntity();
                    if (!(entity instanceof ServerPlayer player)) {
                        return 0;
                    }

                    PlayerAuthState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAuthState());
                    if (state.authenticated) {
                        player.sendSystemMessage(Component.literal("You are already authenticated."));
                        return 1;
                    }

                    player.sendSystemMessage(Component.literal("Creating your MMU auth link..."));
                    requestRegistration(player, state);
                    return 1;
                }))
        );

        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> this.server = startedServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(stoppingServer -> {
            this.server = null;
            this.playerStates.clear();
        });

        ServerPlayConnectionEvents.JOIN.register(this::onJoin);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> playerStates.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onJoin(net.minecraft.server.network.ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.player;
        PlayerAuthState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAuthState());
        state.scheduleImmediateCheck();
        requestStatusCheck(player, state, false);
    }

    private void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerAuthState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAuthState());

            if (!state.authenticated && state.shouldCheckStatus(now)) {
                requestStatusCheck(player, state, false);
            }

            if (state.authenticated) {
                continue;
            }

            enforceSpawnLock(player, state);
        }
    }

    private void requestStatusCheck(ServerPlayer player, PlayerAuthState state, boolean quiet) {
        if (!state.tryBeginStatusRequest()) {
            return;
        }

        apiClient.checkAuthentication(player.getUUID(), (response, error) -> {
            MinecraftServer currentServer = this.server;
            if (currentServer == null) {
                return;
            }

            currentServer.execute(() -> {
                PlayerAuthState currentState = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAuthState());
                currentState.finishStatusRequest();

                if (error != null) {
                    LOGGER.warn("Failed to check auth status for {}", player.getGameProfile().name(), error);
                    return;
                }

                boolean wasAuthenticated = currentState.authenticated;
                currentState.authenticated = response.authenticated();

                if (!wasAuthenticated && currentState.authenticated && !quiet) {
                    player.sendSystemMessage(Component.literal("Authentication confirmed. Dobby is free. Time to get started :)"));
                }
            });
        });
    }

    private void requestRegistration(ServerPlayer player, PlayerAuthState state) {
        apiClient.startRegistration(player.getUUID(), player.getGameProfile().name(), (response, error) -> {
            MinecraftServer currentServer = this.server;
            if (currentServer == null) {
                return;
            }

            currentServer.execute(() -> {
                PlayerAuthState currentState = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAuthState());

                if (error != null) {
                    LOGGER.warn("Failed to start auth registration for {}", player.getGameProfile().name(), error);
                    player.sendSystemMessage(Component.literal("Could not create an auth link. Check server logs."));
                    return;
                }

                if (response.authenticated()) {
                    currentState.authenticated = true;
                    player.sendSystemMessage(Component.literal("You are already authenticated. Welcome back."));
                    return;
                }

                if (response.loginUrl() == null || response.loginUrl().isBlank()) {
                    player.sendSystemMessage(Component.literal("The auth service did not return a login URL."));
                    return;
                }

                currentState.lastLoginUrl = response.loginUrl();
                sendClickableLoginMessage(player, response.loginUrl());
                requestStatusCheck(player, currentState, true);
            });
        });
    }

    private void enforceSpawnLock(ServerPlayer player, PlayerAuthState state) {
        MinecraftServer currentServer = this.server;
        if (currentServer == null) {
            return;
        }

        ServerLevel overworld = currentServer.overworld();
        BlockPos spawn = overworld.getRespawnData().globalPos().pos();
        double spawnX = spawn.getX() + 0.5D;
        double spawnY = spawn.getY();
        double spawnZ = spawn.getZ() + 0.5D;

        boolean wrongWorld = player.level() != overworld;
        boolean farFromSpawn = player.distanceToSqr(spawnX, spawnY, spawnZ) > SPAWN_RADIUS_SQUARED;

        if (!wrongWorld && !farFromSpawn) {
            return;
        }

        player.teleportTo(overworld, spawnX, spawnY, spawnZ, Set.<Relative>of(), 0F, 0.0F, true);
        player.sendSystemMessage(
                Component.literal("You must authenticate before leaving spawn. ")
                        .append(Component.literal("Click here to run /auth")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.RunCommand("/auth"))))
        );
    }

    private void sendClickableLoginMessage(ServerPlayer player, String loginUrl) {
        player.sendSystemMessage(
                Component.literal("Open your MMU registration link: ")
                        .append(Component.literal(loginUrl)
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(loginUrl)))))
        );
    }

    private static final class PlayerAuthState {
        private volatile boolean authenticated;
        private volatile boolean statusRequestInFlight;
        private volatile long nextStatusCheckAt;
        private volatile String lastLoginUrl;

        private PlayerAuthState() {
            this.authenticated = false;
            this.statusRequestInFlight = false;
            this.nextStatusCheckAt = 0L;
            this.lastLoginUrl = null;
        }

        private boolean shouldCheckStatus(long now) {
            return !authenticated && !statusRequestInFlight && now >= nextStatusCheckAt;
        }

        private boolean tryBeginStatusRequest() {
            if (statusRequestInFlight) {
                return false;
            }

            statusRequestInFlight = true;
            nextStatusCheckAt = System.currentTimeMillis() + STATUS_CHECK_INTERVAL_MS;
            return true;
        }

        private void finishStatusRequest() {
            statusRequestInFlight = false;
        }

        private void scheduleImmediateCheck() {
            nextStatusCheckAt = 0L;
        }
    }
}
