package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayEventsGrpc;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.UnlockNextKnowledgeRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.UnlockNextKnowledgeResponse;

import java.util.concurrent.CompletableFuture;

public final class GameplayGrpcBridge {
    private static ManagedChannel apiChannel;
    private static GameplayEventsGrpc.GameplayEventsFutureStub gameplayEvents;

    private GameplayGrpcBridge() {}

    public static synchronized void start() {
        if (apiChannel != null) {
            return;
        }

        String apiTarget = System.getenv().getOrDefault("API_GRPC_TARGET", "api:50051");

        apiChannel = ManagedChannelBuilder
                .forTarget(apiTarget)
                .usePlaintext()
                .build();

        gameplayEvents = GameplayEventsGrpc.newFutureStub(apiChannel);

        MainMod.LOGGER.info("Gameplay gRPC client targeting {}", apiTarget);
    }

    public static synchronized void stop() {
        if (apiChannel != null) {
            apiChannel.shutdownNow();
            apiChannel = null;
        }

        gameplayEvents = null;
    }

    public static CompletableFuture<UnlockNextKnowledgeResponse> unlockNextKnowledge(
            String minecraftUsername,
            String sourceItemId
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        UnlockNextKnowledgeRequest request = UnlockNextKnowledgeRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setSourceItemId(sourceItemId)
                .setUnixMs(System.currentTimeMillis())
                .build();

        var rpc = client.unlockNextKnowledge(request);
        CompletableFuture<UnlockNextKnowledgeResponse> result = new CompletableFuture<>();

        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);

        return result;
    }
}