package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AuthGrpcService extends GrpcHandler {
    static final AuthGrpcService INSTANCE = new AuthGrpcService();

    private final Map<String, PendingJoin> pendingJoins = new ConcurrentHashMap<>();
    private final Map<String, NameAndId> lastSeenProfiles = new ConcurrentHashMap<>();

    private AuthEventsGrpc.AuthEventsFutureStub authEvents;
    private AuthEventsGrpc.AuthEventsBlockingStub blockingAuthEvents;

    private AuthGrpcService() {
    }

    public static void recordLoginAttempt(NameAndId nameAndId, boolean whitelisted) {
        INSTANCE.recordLoginAttemptInternal(nameAndId, whitelisted);
    }

    public static String getPendingCodeFor(String minecraftUsername) {
        return INSTANCE.getPendingCodeForInternal(minecraftUsername);
    }

    public static BanCheck checkPlayerBan(NameAndId nameAndId) {
        return INSTANCE.checkPlayerBanInternal(nameAndId);
    }

    public static void synchronizeBlacklist(NameAndId nameAndId, boolean blacklisted) {
        try {
            INSTANCE.setBlacklistedOnMainThread(nameAndId, blacklisted);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Could not update the Minecraft blacklist for {}", nameAndId.name(), exception);
        }
    }

    @Override
    List<BindableService> serverServices() {
        return List.of(new ModControlEndpoint());
    }

    @Override
    void start(ManagedChannel apiChannel) {
        authEvents = AuthEventsGrpc.newFutureStub(apiChannel);
        blockingAuthEvents = AuthEventsGrpc.newBlockingStub(apiChannel);
    }

    @Override
    void stop() {
        authEvents = null;
        blockingAuthEvents = null;
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

    private BanCheck checkPlayerBanInternal(NameAndId nameAndId) {
        AuthEventsGrpc.AuthEventsBlockingStub client = blockingAuthEvents;
        if (client == null) return null;

        try {
            CheckPlayerBanResponse response = client.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .checkPlayerBan(CheckPlayerBanRequest.newBuilder()
                            .setMinecraftUsername(nameAndId.name())
                            .setUuid(nameAndId.id().toString())
                            .build());
            return new BanCheck(response.getBanned(), response.getPermanent(), response.getExpiresAtUnixMs());
        } catch (RuntimeException exception) {
            MainMod.LOGGER.warn("Could not check the API ban status for {}", nameAndId.name(), exception);
            return null;
        }
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

    private boolean blacklistOnMainThread(String username, String uuid, boolean blacklisted) throws IOException {
        NameAndId nameAndId = profile(username, uuid);
        return setBlacklistedOnMainThread(nameAndId, blacklisted);
    }

    private boolean setBlacklistedOnMainThread(NameAndId nameAndId, boolean blacklisted) throws IOException {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        if (blacklisted) {
            server.getPlayerList().getBans().add(new UserBanListEntry(
                    nameAndId,
                    new Date(),
                    "MMU Minecraft Society website",
                    null,
                    "Account restricted by the committee"
            ));
            var onlinePlayer = server.getPlayerList().getPlayer(nameAndId.id());
            if (onlinePlayer != null) {
                onlinePlayer.connection.disconnect(net.minecraft.network.chat.Component.literal(
                        "Your account has been restricted by the MMU Minecraft Society committee."
                ));
            }
        } else {
            server.getPlayerList().getBans().remove(nameAndId);
        }
        server.getPlayerList().getBans().save();
        return server.getPlayerList().getBans().isBanned(nameAndId);
    }

    private NameAndId profile(String username, String uuid) {
        try {
            String canonicalUuid = uuid.length() == 32
                    ? uuid.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})", "$1-$2-$3-$4-$5")
                    : uuid;
            return new NameAndId(UUID.fromString(canonicalUuid), username);
        } catch (IllegalArgumentException exception) {
            NameAndId lastSeen = lastSeenProfiles.get(normalize(username));
            if (lastSeen == null) throw new IllegalStateException("Player must attempt to join before blacklist changes can be applied");
            return lastSeen;
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record PendingJoin(String username, String code, long expiresAtUnixMs) {
    }

    public record BanCheck(boolean banned, boolean permanent, long expiresAtUnixMs) {
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

        @Override
        public void blacklistPlayer(
                PlayerBlacklistRequest request,
                StreamObserver<PlayerBlacklistResponse> responseObserver
        ) {
            updateBlacklist(request, true, responseObserver);
        }

        @Override
        public void unblacklistPlayer(
                PlayerBlacklistRequest request,
                StreamObserver<PlayerBlacklistResponse> responseObserver
        ) {
            updateBlacklist(request, false, responseObserver);
        }

        private void updateBlacklist(
                PlayerBlacklistRequest request,
                boolean blacklisted,
                StreamObserver<PlayerBlacklistResponse> responseObserver
        ) {
            callOnMainThread(() -> blacklistOnMainThread(
                    request.getMinecraftUsername(),
                    request.getUuid(),
                    blacklisted
            )).whenComplete((stillBlacklisted, error) -> {
                if (error != null) {
                    responseObserver.onError(error);
                    return;
                }

                responseObserver.onNext(PlayerBlacklistResponse.newBuilder()
                        .setBlacklisted(Boolean.TRUE.equals(stillBlacklisted))
                        .build());
                responseObserver.onCompleted();
            });
        }
    }
}
