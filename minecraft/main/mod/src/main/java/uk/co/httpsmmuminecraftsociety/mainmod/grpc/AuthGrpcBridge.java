package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class AuthGrpcBridge {
    private static final Map<String, PendingJoin> pendingJoins = new ConcurrentHashMap<>();
    private static final Map<String, NameAndId> lastSeenProfiles = new ConcurrentHashMap<>();
    private static final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    private static MinecraftServer minecraftServer;
    private static Server grpcServer;
    private static ManagedChannel apiChannel;
    private static AuthEventsGrpc.AuthEventsFutureStub authEvents;

    private AuthGrpcBridge() {}

    public static void start(MinecraftServer server) {
        minecraftServer = server;

        int port = Integer.parseInt(System.getenv().getOrDefault("MOD_GRPC_PORT", "50052"));
        String host = System.getenv().getOrDefault("MOD_GRPC_HOST", "0.0.0.0");
        String apiTarget = System.getenv().getOrDefault("API_GRPC_TARGET", "api:50051");

        try {
            grpcServer = ServerBuilder
                    .forPort(port)
                    .addService(new ModControlService())
                    .addService(new GameplayControlService())
                    .build()
                    .start();

            apiChannel = ManagedChannelBuilder
                    .forTarget(apiTarget)
                    .usePlaintext()
                    .build();

            authEvents = AuthEventsGrpc.newFutureStub(apiChannel);

            MainMod.LOGGER.info("Mod gRPC server listening on {}:{}", host, port);
            MainMod.LOGGER.info("Mod gRPC client targeting {}", apiTarget);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start mod gRPC server", exception);
        }
    }

    public static void stop() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer = null;
        }

        if (apiChannel != null) {
            apiChannel.shutdownNow();
            apiChannel = null;
        }

        minecraftServer = null;
        authEvents = null;
        pendingJoins.clear();
        lastSeenProfiles.clear();
        mainThreadTasks.clear();
    }

    public static void onServerTick() {
        for (int i = 0; i < 64; i++) {
            Runnable task = mainThreadTasks.poll();
            if (task == null) return;
            task.run();
        }
    }

    public static void recordLoginAttempt(NameAndId nameAndId, boolean whitelisted) {
        String username = nameAndId.name();
        lastSeenProfiles.put(normalize(username), nameAndId);

        AuthEventsGrpc.AuthEventsFutureStub client = authEvents;
        if (client == null) return;

        LoginAttemptRequest request = LoginAttemptRequest.newBuilder()
                .setMinecraftUsername(username)
                .setUuid(nameAndId.id().toString())
                .setWhitelisted(whitelisted)
                .setUnixMs(System.currentTimeMillis())
                .build();

        client.reportLoginAttempt(request);
    }

    public static String getPendingCodeFor(String minecraftUsername) {
        PendingJoin pendingJoin = pendingJoins.get(normalize(minecraftUsername));
        if (pendingJoin == null) return null;

        if (pendingJoin.expiresAtUnixMs() < System.currentTimeMillis()) {
            pendingJoins.remove(normalize(minecraftUsername));
            return null;
        }

        return pendingJoin.code();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record PendingJoin(String username, String code, long expiresAtUnixMs) {}

    private static final class ModControlService extends ModControlGrpc.ModControlImplBase {
        @Override
        public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
            responseObserver.onNext(PingResponse.newBuilder()
                    .setService("minecraft-mod")
                    .setUnixMs(System.currentTimeMillis())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void upsertPendingJoin(
                UpsertPendingJoinRequest request,
                StreamObserver<UpsertPendingJoinResponse> responseObserver
        ) {
            String username = request.getMinecraftUsername();
            String key = normalize(username);

            pendingJoins.put(key, new PendingJoin(
                    username,
                    request.getCode(),
                    request.getExpiresAtUnixMs()
            ));

            responseObserver.onNext(UpsertPendingJoinResponse.newBuilder()
                    .setAccepted(true)
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void removePendingJoin(
                RemovePendingJoinRequest request,
                StreamObserver<Empty> responseObserver
        ) {
            pendingJoins.remove(normalize(request.getMinecraftUsername()));
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void whitelistPlayer(
                WhitelistPlayerRequest request,
                StreamObserver<WhitelistPlayerResponse> responseObserver
        ) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();

            mainThreadTasks.add(() -> {
                try {
                    result.complete(whitelistOnMainThread(request.getMinecraftUsername()));
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });

            result.whenComplete((whitelisted, error) -> {
                if (error != null) {
                    responseObserver.onError(error);
                    return;
                }

                responseObserver.onNext(WhitelistPlayerResponse.newBuilder()
                        .setWhitelisted(Boolean.TRUE.equals(whitelisted))
                        .build());
                responseObserver.onCompleted();
            });
        }

        private boolean whitelistOnMainThread(String username) throws IOException
        {
            MinecraftServer server = minecraftServer;
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not available");
            }

            NameAndId nameAndId = lastSeenProfiles.get(normalize(username));
            if (nameAndId == null) {
                throw new IllegalStateException("Player must attempt to join before they can be whitelisted");
            }

            server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(nameAndId));
            server.getPlayerList().getWhiteList().save();

            return server.getPlayerList().isWhiteListed(nameAndId);
        }
    }

    private static final class GameplayControlService extends GameplayControlGrpc.GameplayControlImplBase {
        @Override
        public void grantDailyLoginBonus(
                GrantDailyLoginBonusRequest request,
                StreamObserver<GrantDailyLoginBonusResponse> responseObserver
        ) {
            CompletableFuture<GrantDailyLoginBonusResponse> result = new CompletableFuture<>();

            mainThreadTasks.add(() -> {
                try {
                    result.complete(grantDailyLoginBonusOnMainThread(request));
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });

            result.whenComplete((response, error) -> {
                if (error != null) {
                    responseObserver.onError(error);
                    return;
                }

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            });
        }

        private GrantDailyLoginBonusResponse grantDailyLoginBonusOnMainThread(GrantDailyLoginBonusRequest request) {
            MinecraftServer server = minecraftServer;
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not available");
            }

            String username = request.getMinecraftUsername();
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player == null || player.hasDisconnected()) {
                return GrantDailyLoginBonusResponse.newBuilder()
                        .setGranted(false)
                        .setOnline(false)
                        .setMessage("You have to be online on the server to receive the money.")
                        .build();
            }

            int amount = Math.max(0, request.getAmount());
            if (!MoneyHelper.GainMoney(player, amount)) {
                return GrantDailyLoginBonusResponse.newBuilder()
                        .setGranted(false)
                        .setOnline(true)
                        .setMessage("Could not grant the daily login bonus.")
                        .build();
            }

            return GrantDailyLoginBonusResponse.newBuilder()
                    .setGranted(true)
                    .setOnline(true)
                    .setMessage("You received " + amount + " dabloons.")
                    .build();
        }
    }
}
