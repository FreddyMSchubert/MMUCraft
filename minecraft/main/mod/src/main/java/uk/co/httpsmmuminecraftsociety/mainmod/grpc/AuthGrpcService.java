package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthGrpcService extends GrpcHandler {
    static final AuthGrpcService INSTANCE = new AuthGrpcService();

    private final Map<String, PendingJoin> pendingJoins = new ConcurrentHashMap<>();
    private final Map<String, NameAndId> lastSeenProfiles = new ConcurrentHashMap<>();

    private AuthEventsGrpc.AuthEventsFutureStub authEvents;

    private AuthGrpcService() {
    }

    public static void recordLoginAttempt(NameAndId nameAndId, boolean whitelisted) {
        INSTANCE.recordLoginAttemptInternal(nameAndId, whitelisted);
    }

    public static String getPendingCodeFor(String minecraftUsername) {
        return INSTANCE.getPendingCodeForInternal(minecraftUsername);
    }

    @Override
    List<BindableService> serverServices() {
        return List.of(new ModControlEndpoint());
    }

    @Override
    void start(ManagedChannel apiChannel) {
        authEvents = AuthEventsGrpc.newFutureStub(apiChannel);
    }

    @Override
    void stop() {
        authEvents = null;
        pendingJoins.clear();
        lastSeenProfiles.clear();
    }

    private void recordLoginAttemptInternal(NameAndId nameAndId, boolean whitelisted) {
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

    private String getPendingCodeForInternal(String minecraftUsername) {
        String key = normalize(minecraftUsername);
        PendingJoin pendingJoin = pendingJoins.get(key);
        if (pendingJoin == null) return null;

        if (pendingJoin.expiresAtUnixMs() < System.currentTimeMillis()) {
            pendingJoins.remove(key);
            return null;
        }

        return pendingJoin.code();
    }

    private boolean whitelistOnMainThread(String username) throws IOException {
        MinecraftServer server = minecraftServer();
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

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record PendingJoin(String username, String code, long expiresAtUnixMs) {
    }

    private final class ModControlEndpoint extends ModControlGrpc.ModControlImplBase {
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
            callOnMainThread(() -> whitelistOnMainThread(request.getMinecraftUsername()))
                    .whenComplete((whitelisted, error) -> {
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
    }
}
