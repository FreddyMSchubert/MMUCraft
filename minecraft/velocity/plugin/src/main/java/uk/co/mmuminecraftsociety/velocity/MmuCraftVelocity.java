package uk.co.mmuminecraftsociety.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(
        id = "mmucraft-velocity",
        name = "MMUcraft Velocity",
        version = "1.0.0",
        description = "API-controlled authentication and backend routing for MMUcraft"
)
public final class MmuCraftVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final ApiClient api;
    private final boolean configured;
    private final AtomicBoolean synchronizing = new AtomicBoolean();
    private final Map<String, RegisteredServer> managedServers = new ConcurrentHashMap<>();
    private final Map<String, ApiClient.ServerHealth> health = new ConcurrentHashMap<>();
    private final Map<UUID, String> manualDestinations = new ConcurrentHashMap<>();
    private final Set<Integer> acknowledgedCommands = ConcurrentHashMap.newKeySet();
    private volatile ApiClient.Route route;
    private volatile boolean maintenanceMode;

    @Inject
    public MmuCraftVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        String baseUrl = System.getenv().getOrDefault("MMU_API_BASE_URL", "http://api:8080");
        String secret = System.getenv().getOrDefault("VELOCITY_API_SECRET", "");
        this.api = new ApiClient(baseUrl, secret);
        this.configured = !secret.isBlank();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent ignored) {
        if (!configured) {
            logger.error("VELOCITY_API_SECRET is empty; all player logins will fail closed");
        }
        proxy.getScheduler().buildTask(this, this::synchronize)
                .repeat(Duration.ofSeconds(3))
                .schedule();
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        if (!configured) {
            event.setResult(ResultedEvent.ComponentResult.denied(Messages.authenticationUnavailable()));
            return null;
        }

        CompletableFuture<Void> decision = api.access(
                        event.getPlayer().getUniqueId().toString(),
                        event.getPlayer().getUsername()
                )
                .handle((access, error) -> {
                    if (error != null || access == null) {
                        logger.warn("Could not authenticate {}: {}", event.getPlayer().getUsername(), errorMessage(error));
                        event.setResult(ResultedEvent.ComponentResult.denied(Messages.authenticationUnavailable()));
                    } else if (!"ALLOWED".equals(access.status())) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                Messages.access(access, event.getPlayer().getUsername())
                        ));
                    }
                    return null;
                });
        return EventTask.resumeWhenComplete(decision);
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        RegisteredServer target = targetFor(event.getPlayer());
        event.setInitialServer(target);
        if (target == null) event.getPlayer().disconnect(Messages.unavailable());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = targetFor(event.getPlayer());
        if (target == null) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().disconnect(Messages.unavailable());
            return;
        }
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(target));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        manualDestinations.remove(event.getPlayer().getUniqueId());
    }

    private void synchronize() {
        if (!configured || !synchronizing.compareAndSet(false, true)) return;

        ApiClient.SyncRequest request = new ApiClient.SyncRequest(
                new ArrayList<>(health.values()),
                onlinePlayers(),
                new ArrayList<>(acknowledgedCommands)
        );
        api.sync(request)
                .thenCompose(response -> {
                    acknowledgedCommands.clear();
                    if (response == null) return CompletableFuture.completedFuture(null);
                    applyServers(response.servers() == null ? List.of() : response.servers());
                    if (response.maintenanceMode()) {
                        applyControl(response);
                        return refreshHealth();
                    }
                    return refreshHealth().thenRun(() -> applyControl(response));
                })
                .whenComplete((ignored, error) -> {
                    synchronizing.set(false);
                    if (error != null) logger.warn("Velocity control sync failed: {}", errorMessage(error));
                });
    }

    private void applyControl(ApiClient.SyncResponse response) {
        boolean wasInMaintenance = maintenanceMode;
        maintenanceMode = response.maintenanceMode();
        if (maintenanceMode) {
            manualDestinations.clear();
            proxy.getAllPlayers().forEach(player -> player.disconnect(Messages.maintenance()));
            return;
        }

        ApiClient.Route nextRoute = response.route();
        String oldRevision = route == null ? null : route.revision();
        String nextRevision = nextRoute == null ? null : nextRoute.revision();
        route = nextRoute;
        if (!java.util.Objects.equals(oldRevision, nextRevision) || wasInMaintenance) {
            manualDestinations.clear();
            moveAllToAssignedServer();
        }

        if (response.disconnects() != null) {
            for (ApiClient.DisconnectPlayer disconnect : response.disconnects()) {
                parseUuid(disconnect.playerUuid()).flatMap(proxy::getPlayer).ifPresent(player ->
                        player.disconnect(Messages.restriction(disconnect.status(), disconnect.expiresAtUnixMs()))
                );
            }
        }

        if (response.commands() != null) {
            for (ApiClient.MoveCommand command : response.commands()) {
                execute(command);
                acknowledgedCommands.add(command.id());
            }
        }
    }

    private void applyServers(List<ApiClient.BackendServer> desiredServers) {
        Set<String> desiredNames = new HashSet<>();
        for (ApiClient.BackendServer backend : desiredServers) {
            desiredNames.add(backend.name());
            HostPort address = HostPort.parse(backend.address());
            RegisteredServer existing = proxy.getServer(backend.name()).orElse(null);
            if (existing != null && sameAddress(existing, address)) {
                managedServers.put(backend.name(), existing);
                continue;
            }
            if (existing != null) proxy.unregisterServer(existing.getServerInfo());
            RegisteredServer registered = proxy.registerServer(new ServerInfo(
                    backend.name(),
                    InetSocketAddress.createUnresolved(address.host(), address.port())
            ));
            managedServers.put(backend.name(), registered);
        }

        for (String name : new HashSet<>(managedServers.keySet())) {
            if (desiredNames.contains(name)) continue;
            RegisteredServer removed = managedServers.remove(name);
            if (removed != null) proxy.unregisterServer(removed.getServerInfo());
            health.remove(name);
        }
    }

    private CompletableFuture<Void> refreshHealth() {
        Map<String, ApiClient.ServerHealth> next = new ConcurrentHashMap<>();
        List<CompletableFuture<Object>> pings = managedServers.entrySet().stream().map(entry -> {
            long started = System.nanoTime();
            return entry.getValue().ping(PingOptions.builder().timeout(Duration.ofSeconds(2)).build())
                    .handle((ping, error) -> {
                        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                        next.put(entry.getKey(), new ApiClient.ServerHealth(
                                entry.getKey(),
                                error == null,
                                error == null ? latencyMs : null,
                                error == null ? null : errorMessage(error)
                        ));
                        return null;
                    });
        }).toList();

        return CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    health.clear();
                    health.putAll(next);
                });
    }

    private List<ApiClient.OnlinePlayer> onlinePlayers() {
        return proxy.getAllPlayers().stream().flatMap(player -> player.getCurrentServer().stream().map(connection ->
                new ApiClient.OnlinePlayer(
                        player.getUniqueId().toString(),
                        player.getUsername(),
                        connection.getServerInfo().getName()
                )
        )).toList();
    }

    private void execute(ApiClient.MoveCommand command) {
        parseUuid(command.playerUuid()).flatMap(proxy::getPlayer).ifPresent(player -> {
            manualDestinations.put(player.getUniqueId(), command.targetServerName());
            RegisteredServer target = targetFor(player);
            if (target != null) player.createConnectionRequest(target).fireAndForget();
        });
    }

    private void moveAllToAssignedServer() {
        for (Player player : proxy.getAllPlayers()) {
            RegisteredServer target = targetFor(player);
            if (target == null) {
                player.disconnect(Messages.unavailable());
                continue;
            }
            if (player.getCurrentServer().map(connection -> connection.getServerInfo().equals(target.getServerInfo())).orElse(false)) {
                continue;
            }
            player.createConnectionRequest(target).fireAndForget();
        }
    }

    private RegisteredServer targetFor(Player player) {
        if (maintenanceMode) return null;
        String name = manualDestinations.get(player.getUniqueId());
        if (name == null && route != null) name = route.targetServerName();
        if (name == null || !health.getOrDefault(name, new ApiClient.ServerHealth(name, false, null, null)).online()) {
            return null;
        }
        return managedServers.get(name);
    }

    private boolean sameAddress(RegisteredServer server, HostPort desired) {
        InetSocketAddress current = server.getServerInfo().getAddress();
        return current.getHostString().equalsIgnoreCase(desired.host()) && current.getPort() == desired.port();
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        try {
            String canonical = value.length() == 32
                    ? value.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})", "$1-$2-$3-$4-$5")
                    : value;
            return java.util.Optional.of(UUID.fromString(canonical));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static String errorMessage(Throwable error) {
        if (error == null) return "unknown error";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record HostPort(String host, int port) {
        static HostPort parse(String address) {
            int separator = address.lastIndexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Invalid backend address: " + address);
            return new HostPort(address.substring(0, separator), Integer.parseInt(address.substring(separator + 1)));
        }
    }
}
