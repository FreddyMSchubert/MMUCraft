package uk.co.mmuminecraftsociety.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Plugin(
        id = "mmucraft-velocity",
        name = "MMUcraft Velocity",
        version = "1.0.0",
        description = "API-controlled authentication and backend routing for MMUcraft"
)
public final class MmuCraftVelocity {
    private static final MinecraftChannelIdentifier MODE_CHANNEL = MinecraftChannelIdentifier.from("mmucraft:waiting_mode");
    private final ProxyServer proxy;
    private final Logger logger;
    private final ApiClient api;
    private final boolean configured;
    private final int waitingRoomLimit;
    private final AtomicBoolean synchronizing = new AtomicBoolean();
    private final Map<String, RegisteredServer> managedServers = new ConcurrentHashMap<>();
    private final Map<String, ApiClient.ServerHealth> health = new ConcurrentHashMap<>();
    private final Map<UUID, String> manualDestinations = new ConcurrentHashMap<>();
    private final Map<UUID, String> loginDestinations = new ConcurrentHashMap<>();
    private final Set<Long> acknowledgedCommands = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CapacityRole> capacityRoles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Set<UUID> admissions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, QueueEntry> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, QueueEntry> visitors = new ConcurrentHashMap<>();
    private final AtomicLong queueOrder = new AtomicLong();
    private int admissionTurn;
    private final Map<UUID, String> shownPositions = new ConcurrentHashMap<>();
    private final Map<UUID, CapacityWarning> capacityWarnings = new ConcurrentHashMap<>();
    private volatile ApiClient.Route route;
    private volatile boolean maintenanceMode;
    private volatile int maxPlayers = 42;
    private String deploymentId;
    private boolean deploymentHadPlayers;

    @Inject
    public MmuCraftVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        String baseUrl = System.getenv().getOrDefault("MMU_API_BASE_URL", "http://api:8080");
        String secret = System.getenv().getOrDefault("VELOCITY_API_SECRET", "");
        this.api = new ApiClient(baseUrl, secret);
        this.configured = !secret.isBlank();
        this.waitingRoomLimit = Math.max(1, Math.min(100,
                Integer.parseInt(System.getenv().getOrDefault("MMU_WAITING_ROOM_LIMIT", "100"))));
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent ignored) {
        proxy.getChannelRegistrar().register(MODE_CHANNEL);
        for (String command : List.of("glist", "send", "server", "shutdown", "velocity")) {
            proxy.getCommandManager().unregister(command);
        }
        if (!configured) {
            logger.error("VELOCITY_API_SECRET is empty; all player logins will fail closed");
        }
        proxy.getScheduler().buildTask(this, this::synchronize)
                .repeat(Duration.ofSeconds(3))
                .schedule();
        proxy.getScheduler().buildTask(this, this::checkDeployment)
                .repeat(Duration.ofSeconds(1))
                .schedule();
        proxy.getScheduler().buildTask(this, this::checkCapacity)
                .repeat(Duration.ofSeconds(1))
                .schedule();
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Component update = deploymentMessage();
        if (update != null) {
            event.setResult(ResultedEvent.ComponentResult.denied(update));
            return null;
        }
        if (!configured) {
            event.setResult(ResultedEvent.ComponentResult.denied(Messages.authenticationUnavailable()));
            return null;
        }

        CompletableFuture<Void> decision = api.access(
                        event.getPlayer().getUniqueId().toString(),
                        event.getPlayer().getUsername()
                )
                .handle((access, error) -> {
                    Component currentUpdate = deploymentMessage();
                    if (currentUpdate != null) {
                        event.setResult(ResultedEvent.ComponentResult.denied(currentUpdate));
                    } else if (error != null || access == null) {
                        logger.warn("Could not authenticate {}: {}", event.getPlayer().getUsername(), errorMessage(error));
                        event.setResult(ResultedEvent.ComponentResult.denied(Messages.authenticationUnavailable()));
                    } else if (!"ALLOWED".equals(access.status())) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                Messages.access(access, event.getPlayer().getUsername())
                        ));
                    } else if (!admit(event.getPlayer(), new CapacityRole(access.member(), access.exempt()),
                            access.preferredServerName())) {
                        event.setResult(ResultedEvent.ComponentResult.denied(Messages.waitingRoomUnavailable()));
                    } else {
                        loginDestinations.put(event.getPlayer().getUniqueId(),
                                waiting.containsKey(event.getPlayer().getUniqueId()) ? "waiting-room"
                                        : "surprising-saturday".equals(access.preferredServerName())
                                                ? "surprising-saturday" : "main");
                    }
                    return waiting.containsKey(event.getPlayer().getUniqueId());
                }).thenCompose(queued -> {
                    if (!queued) return CompletableFuture.completedFuture(null);
                    return api.markQueued(event.getPlayer().getUniqueId().toString(),
                            event.getPlayer().getUsername()).handle((response, error) -> {
                        if (error != null || response == null || !response.ok()) {
                            logger.warn("Could not mark {} as queued: {}", event.getPlayer().getUsername(),
                                    errorMessage(error));
                            clearPlayerState(event.getPlayer().getUniqueId());
                            event.setResult(ResultedEvent.ComponentResult.denied(Messages.authenticationUnavailable()));
                        }
                        return null;
                    });
                });
        return EventTask.resumeWhenComplete(decision);
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Component update = deploymentMessage();
        if (update != null) {
            event.setInitialServer(null);
            event.getPlayer().disconnect(update);
            return;
        }
        RegisteredServer target = targetFor(event.getPlayer());
        event.setInitialServer(target);
        if (target == null) event.getPlayer().disconnect(Messages.unavailable());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Component update = deploymentMessage();
        if (update != null) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().disconnect(update);
            return;
        }
        RegisteredServer target = targetFor(event.getPlayer());
        if (target == null) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().disconnect(Messages.unavailable());
            return;
        }
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(target));
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Component update = deploymentMessage();
        Component reason = event.getServerKickReason().orElse(null);
        String reasonText = reason == null ? "" : PlainTextComponentSerializer.plainText().serialize(reason);
        if (update != null) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(update));
        } else if (reasonText.startsWith("Outdated client!") || reasonText.startsWith("Outdated server!")) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(Messages.wrongVersion(reason)));
        } else if (event.getServer().getServerInfo().getName().equals("surprising-saturday")
                && managedServers.containsKey("main")
                && health.getOrDefault("main", new ApiClient.ServerHealth("main", false, null, null)).online()) {
            manualDestinations.put(event.getPlayer().getUniqueId(), "main");
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(managedServers.get("main")));
        } else if (event.getPlayer().getCurrentServer().isEmpty()) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(Messages.unavailable()));
        } else if (event.kickedDuringServerConnect()) {
            event.setResult(KickedFromServerEvent.Notify.create(Messages.transferUnavailable()));
        } else {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(
                    Messages.disconnected(reason)
            ));
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();
        if ("waiting-room".equals(serverName)) return;
        visitors.remove(uuid);
        admissions.add(uuid);
        joinedAt.putIfAbsent(uuid, System.currentTimeMillis());
        loginDestinations.put(uuid, serverName);
        api.connected(uuid.toString(), serverName)
                .exceptionally(error -> {
                    logger.warn("Could not save {}'s server: {}", event.getPlayer().getUsername(), errorMessage(error));
                    return null;
                });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    private void clearPlayerState(UUID uuid) {
        capacityRoles.remove(uuid);
        joinedAt.remove(uuid);
        admissions.remove(uuid);
        waiting.remove(uuid);
        visitors.remove(uuid);
        shownPositions.remove(uuid);
        capacityWarnings.remove(uuid);
        manualDestinations.remove(uuid);
        loginDestinations.remove(uuid);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!MODE_CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection backend)
                || !"waiting-room".equals(backend.getServerInfo().getName())
                || event.getData().length != 1) return;
        byte mode = event.getData()[0];
        if (mode != 0 && mode != 1) return;
        Player player = backend.getPlayer();
        UUID uuid = player.getUniqueId();
        synchronized (this) {
            if (mode == 1) {
                QueueEntry entry = waiting.remove(uuid);
                if (entry == null) return;
                visitors.put(uuid, entry);
                shownPositions.remove(uuid);
                player.sendActionBar(Component.empty());
            } else {
                QueueEntry entry = visitors.remove(uuid);
                if (entry == null) return;
                CapacityRole role = capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST);
                waiting.put(uuid, new QueueEntry(role.member(), queueOrder.incrementAndGet(), entry.destination()));
            }
        }
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        Component update = deploymentMessage();
        var ping = event.getPing().asBuilder()
                .onlinePlayers((int) proxy.getAllPlayers().stream()
                        .filter(player -> admissions.contains(player.getUniqueId()))
                        .filter(player -> player.getCurrentServer().stream().noneMatch(connection ->
                                "waiting-room".equals(connection.getServerInfo().getName())))
                        .filter(player -> !capacityRoles.getOrDefault(player.getUniqueId(), CapacityRole.LOWEST).exempt())
                        .count())
                .maximumPlayers(maxPlayers);
        if (update != null) ping.description(update);
        event.setPing(ping.build());
    }

    private Component deploymentMessage() {
        try {
            DeploymentState state = DeploymentState.read();
            return state == null ? null : Messages.updating(state.startedAt(), mainOnline());
        } catch (IOException error) {
            return Messages.updateStateUnavailable();
        }
    }

    private boolean mainOnline() {
        ApiClient.ServerHealth main = health.get("main");
        return main != null && main.online();
    }

    private synchronized void checkDeployment() {
        try {
            DeploymentState state = DeploymentState.read();
            if (state == null) {
                deploymentId = null;
                deploymentHadPlayers = false;
                return;
            }
            if (!state.id().equals(deploymentId)) {
                deploymentId = state.id();
                deploymentHadPlayers = false;
            }
            List<Player> players = new ArrayList<>(proxy.getAllPlayers());
            deploymentHadPlayers |= !players.isEmpty();
            Component message = deploymentMessage();
            if (message == null) return;
            players.forEach(player -> player.disconnect(message));
            if (proxy.getPlayerCount() == 0) state.acknowledge(deploymentHadPlayers,
                    configured && route != null && routeTargetOnline());
        } catch (IOException error) {
            logger.error("Could not read or acknowledge deployment state", error);
        }
    }

    private void synchronize() {
        if (!configured || !synchronizing.compareAndSet(false, true)) return;

        List<Long> sentAcknowledgements = new ArrayList<>(acknowledgedCommands);
        ApiClient.SyncRequest request = new ApiClient.SyncRequest(
                new ArrayList<>(health.values()),
                onlinePlayers(),
                sentAcknowledgements
        );
        api.sync(request)
                .exceptionally(error -> {
                    logger.warn("Velocity control sync failed: {}", errorMessage(error));
                    return null;
                })
                .thenCompose(response -> {
                    if (response == null) return refreshHealth();
                    acknowledgedCommands.removeAll(sentAcknowledgements);
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
        if (response.maxPlayers() != null) maxPlayers = Math.max(0, response.maxPlayers());
        if (response.playerRoles() != null) {
            for (ApiClient.PlayerRole player : response.playerRoles()) {
                parseUuid(player.uuid()).ifPresent(uuid -> {
                    capacityRoles.put(uuid, new CapacityRole(player.member(), player.exempt()));
                    waiting.computeIfPresent(uuid, (id, entry) ->
                            new QueueEntry(player.member(), entry.order(), entry.destination()));
                });
            }
        }
        if (deploymentMessage() != null) {
            route = response.route();
            maintenanceMode = response.maintenanceMode();
            return;
        }
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
        String oldTarget = route == null ? null : route.targetServerName();
        String nextTarget = nextRoute == null ? null : nextRoute.targetServerName();
        route = nextRoute;
        if (!Objects.equals(oldRevision, nextRevision) || wasInMaintenance
                || ("surprising-saturday".equals(oldTarget) && "main".equals(nextTarget))) {
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
            response.commands().forEach(this::execute);
        }
    }

    private synchronized boolean admit(Player player, CapacityRole role, String destination) {
        UUID uuid = player.getUniqueId();
        if (!role.exempt() && (!waiting.isEmpty() || occupied() >= maxPlayers)) {
            if (!serverOnline("waiting-room") || waitingRoomPopulation() >= waitingRoomLimit) return false;
            waiting.put(uuid, new QueueEntry(role.member(), queueOrder.incrementAndGet(), destination));
        } else {
            admissions.add(uuid);
        }
        capacityRoles.put(uuid, role);
        return true;
    }

    private long occupied() {
        return admissions.stream()
                .filter(uuid -> !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt())
                .count();
    }

    private int waitingRoomPopulation() {
        Set<UUID> present = new HashSet<>(waiting.keySet());
        for (Player player : proxy.getAllPlayers()) {
            if (player.getCurrentServer().stream().anyMatch(connection ->
                    "waiting-room".equals(connection.getServerInfo().getName())))
                present.add(player.getUniqueId());
        }
        return present.size();
    }

    private synchronized void checkCapacity() {
        if (maintenanceMode || deploymentMessage() != null) {
            capacityWarnings.clear();
            return;
        }
        long count = occupied();
        int needed = (int) Math.max(0, count - maxPlayers);
        if (!waiting.isEmpty() && count >= maxPlayers) needed++;
        Set<UUID> selected = new HashSet<>();
        long now = System.currentTimeMillis();
        List<UUID> victims = admissions.stream()
                .filter(uuid -> !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt())
                .filter(uuid -> joinedAt.containsKey(uuid))
                .filter(uuid -> now - joinedAt.get(uuid) >= minimumPlaytime(uuid))
                .sorted(Comparator.<UUID>comparingLong(uuid ->
                        now - joinedAt.get(uuid) - minimumPlaytime(uuid)).reversed()
                        .thenComparing(UUID::toString))
                .limit(needed)
                .toList();
        for (UUID uuid : victims) {
            Player victim = proxy.getPlayer(uuid).orElse(null);
            if (victim == null) continue;
            selected.add(uuid);
            CapacityWarning warning = capacityWarnings.computeIfAbsent(uuid, ignored -> {
                victim.sendMessage(Messages.capacityWarning("3 minutes"));
                return new CapacityWarning(now + Duration.ofMinutes(3).toMillis());
            });
            long remaining = warning.deadline() - now;
            if (remaining <= 0 && warning.stage() < 4) {
                warning.setStage(4);
                victim.disconnect(Messages.capacityKick());
            } else if (remaining <= 10_000 && warning.stage() < 3) {
                victim.sendMessage(Messages.capacityWarning("10 seconds"));
                warning.setStage(3);
            } else if (remaining <= 60_000 && warning.stage() < 2) {
                victim.sendMessage(Messages.capacityWarning("1 minute"));
                warning.setStage(2);
            } else if (remaining <= 120_000 && warning.stage() < 1) {
                victim.sendMessage(Messages.capacityWarning("2 minutes"));
                warning.setStage(1);
            }
        }
        for (UUID uuid : new HashSet<>(capacityWarnings.keySet())) {
            if (selected.contains(uuid)) continue;
            capacityWarnings.remove(uuid);
            Player player = proxy.getPlayer(uuid).orElse(null);
            if (player == null) continue;
            player.sendMessage(Messages.capacityCancelled());
        }
        fillVacancies();
        showWaitingPositions();
    }

    private long minimumPlaytime(UUID uuid) {
        return Duration.ofMinutes(capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).member() ? 80 : 40)
                .toMillis();
    }

    private void fillVacancies() {
        while (occupied() < maxPlayers && !waiting.isEmpty()) {
            QueueEntry next = nextQueued();
            if (next == null) return;
            UUID uuid = waiting.entrySet().stream()
                    .filter(entry -> entry.getValue() == next)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (uuid == null) return;
            Player player = proxy.getPlayer(uuid).orElse(null);
            if (player == null) { waiting.remove(uuid); continue; }
            String destination = "surprising-saturday".equals(next.destination())
                    && route != null && route.eventOpen() ? "surprising-saturday" : "main";
            if (!serverOnline(destination)) {
                destination = "main".equals(destination) && route != null && route.eventOpen()
                        && serverOnline("surprising-saturday") ? "surprising-saturday" : destination;
                if (!serverOnline(destination)) return;
            }
            boolean bothQueues = waiting.values().stream().anyMatch(QueueEntry::member)
                    && waiting.values().stream().anyMatch(entry -> !entry.member());
            waiting.remove(uuid);
            shownPositions.remove(uuid);
            admissions.add(uuid);
            manualDestinations.put(uuid, destination);
            RegisteredServer target = managedServers.get(destination);
            if (target == null) { admissions.remove(uuid); waiting.put(uuid, next); return; }
            admissionTurn = bothQueues ? (admissionTurn + 1) % 3 : 0;
            String selectedDestination = destination;
            player.createConnectionRequest(target).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    manualDestinations.remove(uuid, selectedDestination);
                } else {
                    synchronized (this) {
                        admissions.remove(uuid);
                        manualDestinations.remove(uuid, selectedDestination);
                        if (proxy.getPlayer(uuid).isPresent()) waiting.put(uuid, next);
                    }
                }
            });
        }
    }

    private QueueEntry nextQueued() {
        boolean hasMembers = waiting.values().stream().anyMatch(QueueEntry::member);
        boolean hasNormal = waiting.values().stream().anyMatch(entry -> !entry.member());
        boolean chooseMember = hasMembers && (!hasNormal || admissionTurn != 2);
        return waiting.values().stream()
                .filter(entry -> entry.member() == chooseMember)
                .min(Comparator.comparingLong(QueueEntry::order)).orElse(null);
    }

    private void showWaitingPositions() {
        for (boolean members : List.of(true, false)) {
            List<Map.Entry<UUID, QueueEntry>> queue = waiting.entrySet().stream()
                    .filter(entry -> entry.getValue().member() == members)
                    .sorted(Comparator.comparingLong(entry -> entry.getValue().order()))
                    .toList();
            for (int i = 0; i < queue.size(); i++) {
                UUID uuid = queue.get(i).getKey();
                Player player = proxy.getPlayer(uuid).orElse(null);
                if (player == null) continue;
                String label = members ? "Members queue" : "Queue";
                Component status = Component.text(label + ": position " + (i + 1));
                player.sendActionBar(status);
                String position = label + ":" + (i + 1);
                String previous = shownPositions.put(uuid, position);
                if (!position.equals(previous)) player.sendMessage(status);
            }
        }
    }

    private record QueueEntry(boolean member, long order, String destination) { }

    private record CapacityRole(boolean member, boolean exempt) {
        static final CapacityRole LOWEST = new CapacityRole(false, false);
    }

    private static final class CapacityWarning {
        private final long deadline;
        private int stage;

        CapacityWarning(long deadline) { this.deadline = deadline; }
        long deadline() { return deadline; }
        int stage() { return stage; }
        void setStage(int stage) { this.stage = stage; }
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
        return proxy.getAllPlayers().stream().flatMap(player ->
                player.getCurrentServer().stream().map(connection -> new ApiClient.OnlinePlayer(
                        player.getUniqueId().toString(),
                        player.getUsername(),
                        connection.getServerInfo().getName(),
                        "waiting-room".equals(connection.getServerInfo().getName())
                                ? visitors.containsKey(player.getUniqueId()) ? "visiting" : "queued"
                                : "playing"
                ))
        ).toList();
    }

    private void execute(ApiClient.MoveCommand command) {
        Optional<Player> player = parseUuid(command.playerUuid()).flatMap(proxy::getPlayer);
        if (player.isEmpty()) {
            acknowledgedCommands.add(command.id());
            return;
        }

        Player targetPlayer = player.get();
        if (waiting.containsKey(targetPlayer.getUniqueId())) return;
        RegisteredServer target = managedServers.get(command.targetServerName());
        if (target == null || !serverOnline(command.targetServerName())) return;
        manualDestinations.put(targetPlayer.getUniqueId(), command.targetServerName());
        if (targetPlayer.getCurrentServer()
                .map(connection -> connection.getServerInfo().equals(target.getServerInfo()))
                .orElse(false)) {
            acknowledgedCommands.add(command.id());
            return;
        }

        targetPlayer.createConnectionRequest(target).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                acknowledgedCommands.add(command.id());
            } else {
                manualDestinations.remove(targetPlayer.getUniqueId(), command.targetServerName());
            }
        });
    }

    private void moveAllToAssignedServer() {
        for (Player player : proxy.getAllPlayers()) {
            RegisteredServer target = targetFor(player);
            if (target == null) {
                player.disconnect(Messages.unavailable());
                continue;
            }
            if (player.getCurrentServer()
                    .map(connection -> connection.getServerInfo().equals(target.getServerInfo()))
                    .orElse(false)) {
                continue;
            }
            player.createConnectionRequest(target).fireAndForget();
        }
    }

    private RegisteredServer targetFor(Player player) {
        if (maintenanceMode) return null;
        UUID uuid = player.getUniqueId();
        if (waiting.containsKey(uuid) || visitors.containsKey(uuid)) return managedServers.get("waiting-room");
        String name = manualDestinations.get(uuid);
        if (name == null && player.getCurrentServer().isEmpty()) {
            name = loginDestinations.getOrDefault(uuid, "main");
            if ("surprising-saturday".equals(name) && (route == null || !route.eventOpen())) name = "main";
        }
        if (name == null && route != null) name = route.targetServerName();
        if (name != null && !serverOnline(name)) {
            String alternative = "main".equals(name) ? "surprising-saturday" : "main";
            boolean eventOpen = route != null && route.eventOpen();
            if ("main".equals(alternative) || eventOpen) name = alternative;
        }
        if (name == null || !health.getOrDefault(
                name,
                new ApiClient.ServerHealth(name, false, null, null)
        ).online()) return null;
        return managedServers.get(name);
    }

    private boolean serverOnline(String name) {
        ApiClient.ServerHealth server = health.get(name);
        return server != null && server.online();
    }

    private boolean routeTargetOnline() {
        return route != null && (serverOnline(route.targetServerName())
                || route.eventOpen() && serverOnline("surprising-saturday"));
    }

    private boolean sameAddress(RegisteredServer server, HostPort desired) {
        InetSocketAddress current = server.getServerInfo().getAddress();
        return current.getHostString().equalsIgnoreCase(desired.host()) && current.getPort() == desired.port();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            String canonical = value.length() == 32
                    ? value.replaceFirst(
                            "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                            "$1-$2-$3-$4-$5"
                    )
                    : value;
            return Optional.of(UUID.fromString(canonical));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
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
