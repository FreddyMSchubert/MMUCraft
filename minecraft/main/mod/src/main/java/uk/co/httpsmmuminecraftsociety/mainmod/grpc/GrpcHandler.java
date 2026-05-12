package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class GrpcHandler {
    List<BindableService> serverServices() {
        return List.of();
    }

    void start(ManagedChannel apiChannel) {
    }

    void stop() {
    }

    protected MinecraftServer minecraftServer() {
        return GrpcBridge.minecraftServer();
    }

    protected void runOnMainThread(Runnable task) {
        GrpcBridge.runOnMainThread(task);
    }

    protected <T> CompletableFuture<T> callOnMainThread(ThrowingSupplier<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();

        runOnMainThread(() -> {
            try {
                result.complete(supplier.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });

        return result;
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
