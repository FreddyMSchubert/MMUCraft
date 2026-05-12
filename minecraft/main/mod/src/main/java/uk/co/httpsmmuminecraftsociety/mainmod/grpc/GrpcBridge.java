package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import net.minecraft.server.MinecraftServer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GrpcBridge {
    private static final List<GrpcHandler> HANDLERS = List.of(
            AuthGrpcService.INSTANCE,
            GameplayGrpcService.INSTANCE
    );

    private static final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    private static MinecraftServer minecraftServer;
    private static Server grpcServer;
    private static ManagedChannel apiChannel;

    private GrpcBridge() {
    }

    public static void start(MinecraftServer server) {
        minecraftServer = server;

        int port = Integer.parseInt(System.getenv().getOrDefault("MOD_GRPC_PORT", "50052"));
        String host = System.getenv().getOrDefault("MOD_GRPC_HOST", "0.0.0.0");
        String apiTarget = System.getenv().getOrDefault("API_GRPC_TARGET", "api:50051");

        try {
            ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);
            for (GrpcHandler handler : HANDLERS) {
                for (BindableService service : handler.serverServices()) {
                    serverBuilder.addService(service);
                }
            }

            grpcServer = serverBuilder.build().start();

            apiChannel = ManagedChannelBuilder
                    .forTarget(apiTarget)
                    .usePlaintext()
                    .build();

            for (GrpcHandler handler : HANDLERS) {
                handler.start(apiChannel);
            }

            MainMod.LOGGER.info("Mod gRPC server listening on {}:{}", host, port);
            MainMod.LOGGER.info("Mod gRPC client targeting {}", apiTarget);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start mod gRPC", exception);
        }
    }

    public static void stop() {
        for (GrpcHandler handler : HANDLERS) {
            handler.stop();
        }

        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer = null;
        }

        if (apiChannel != null) {
            apiChannel.shutdownNow();
            apiChannel = null;
        }

        minecraftServer = null;
        mainThreadTasks.clear();
    }

    public static void onServerTick() {
        for (int i = 0; i < 64; i++) {
            Runnable task = mainThreadTasks.poll();
            if (task == null) return;
            task.run();
        }
    }

    static MinecraftServer minecraftServer() {
        return minecraftServer;
    }

    static void runOnMainThread(Runnable task) {
        mainThreadTasks.add(task);
    }
}
