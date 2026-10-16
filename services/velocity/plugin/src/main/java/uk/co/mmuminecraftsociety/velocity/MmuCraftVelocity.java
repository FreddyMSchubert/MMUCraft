package uk.co.mmuminecraftsociety.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
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
    private static final int WAITING_ROOM_LIMIT = 100;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ApiClient api;
    private final boolean configured;
    private final AtomicBoolean synchronizing = new AtomicBoolean();
    private final Map<String, RegisteredServer> managedServers = new ConcurrentHashMap<>();
    private final Map<String, ApiClient.ServerHealth> health = new ConcurrentHashMap<>();
    private final Map<UUID, String> manualDestinations = new ConcurrentHashMap<>();
    private final Map<UUID, String> temporaryDestinations = new ConcurrentHashMap<>();
    private final Set<UUID> pendingEventAdmins = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> sessionIds = new ConcurrentHashMap<>();
    private final Map<UUID, String> loginDestinations = new ConcurrentHashMap<>();
    private final Set<Long> acknowledgedCommands = ConcurrentHashMap.newKeySet();
    private final Set<Long> connectingCommands = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CapacityRole> capacityRoles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Set<UUID> admissions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> admissionDestinations = new ConcurrentHashMap<>();
    private final Map<String, UUID> pendingAdmissions = new ConcurrentHashMap<>();
    private final Map<UUID, QueueEntry> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, QueueEntry> visitors = new ConcurrentHashMap<>();
    private final AtomicLong queueOrder = new AtomicLong();
    private final Map<String, Integer> admissionTurns = new ConcurrentHashMap<>();
    private final Map<UUID, String> shownPositions = new ConcurrentHashMap<>();
    private final Map<UUID, CapacityWarning> capacityWarnings = new ConcurrentHashMap<>();
    private volatile ApiClient.Route route;
    private volatile boolean maintenanceMode;
    private final Map<String, Integer> maxPlayers = new ConcurrentHashMap<>(Map.of("main", 42, "surprising-saturday", 42));
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
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;
        String serverName = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName()).orElse("proxy");
        api.recordCommand(player.getUniqueId().toString(), player.getUsername(), serverName, event.getCommand())
                .exceptionally(error -> {
                    logger.warn("Could not save player command to the command log", error);
                    return null;
                });
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
                                        : admissionDestinations.get(event.getPlayer().getUniqueId()));
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
            UUID uuid = event.getPlayer().getUniqueId();
            boolean retryAdmin = event.kickedDuringServerConnect()
                    && capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt();
            if (retryAdmin) pendingEventAdmins.add(uuid);
            else {
                temporaryDestinations.remove(uuid);
                manualDestinations.put(uuid, "main");
            }
            RegisteredServer fallback = retryAdmin ? managedServers.get("main") : targetFor(event.getPlayer());
            event.setResult(fallback == null || fallback.getServerInfo().equals(event.getServer().getServerInfo())
                    ? KickedFromServerEvent.DisconnectPlayer.create(Messages.unavailable())
                    : KickedFromServerEvent.RedirectPlayer.create(fallback));
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
        sessionIds.computeIfAbsent(uuid, ignored -> UUID.randomUUID().toString());
        String serverName = event.getServer().getServerInfo().getName();
        if ("waiting-room".equals(serverName)) {
            pendingAdmissions.values().removeIf(uuid::equals);
            admissions.remove(uuid);
            admissionDestinations.remove(uuid);
            joinedAt.remove(uuid);
            capacityWarnings.remove(uuid);
            return;
        }
        waiting.remove(uuid);
        visitors.remove(uuid);
        if ("surprising-saturday".equals(serverName)) pendingEventAdmins.remove(uuid);
        pendingAdmissions.values().removeIf(uuid::equals);
        admissions.add(uuid);
        admissionDestinations.put(uuid, serverName);
        joinedAt.put(uuid, System.currentTimeMillis());
        loginDestinations.put(uuid, serverName);
        if ("surprising-saturday".equals(serverName)
                && (route == null || !route.eventOpen())
                && !"surprising-saturday".equals(temporaryDestinations.get(uuid))) {
            RegisteredServer target = targetFor(event.getPlayer());
            if (target == null || target.getServerInfo().equals(event.getServer().getServerInfo()))
                event.getPlayer().disconnect(Messages.unavailable());
            else event.getPlayer().createConnectionRequest(target).connect().thenAccept(result -> {
                if (!result.isSuccessful()) event.getPlayer().disconnect(Messages.unavailable());
            });
            return;
        }
        api.connected(uuid.toString(), serverName, temporaryDestinations.containsKey(uuid))
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
        admissionDestinations.remove(uuid);
        pendingAdmissions.values().removeIf(uuid::equals);
        waiting.remove(uuid);
        visitors.remove(uuid);
        shownPositions.remove(uuid);
        capacityWarnings.remove(uuid);
        manualDestinations.remove(uuid);
        temporaryDestinations.remove(uuid);
        pendingEventAdmins.remove(uuid);
        sessionIds.remove(uuid);
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
                waiting.put(uuid, new QueueEntry(role.member(), queueOrder.incrementAndGet(),
                        availableDestination(uuid, entry.destination())));
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
                .maximumPlayers(limit("main") + (route != null && route.eventOpen()
                        && serverOnline("surprising-saturday")
                        ? limit("surprising-saturday") : 0));
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

    private synchronized void applyControl(ApiClient.SyncResponse response) {
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
            manualDestinations.keySet().removeIf(uuid ->
                    !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt());
            temporaryDestinations.keySet().removeIf(uuid ->
                    !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt());
            proxy.getAllPlayers().stream()
                    .filter(player -> !capacityRoles.getOrDefault(player.getUniqueId(), CapacityRole.LOWEST).exempt())
                    .forEach(player -> player.disconnect(Messages.maintenance()));
        }

        ApiClient.Route nextRoute = response.route();
        String oldRevision = route == null ? null : route.revision();
        String nextRevision = nextRoute == null ? null : nextRoute.revision();
        String oldTarget = route == null ? null : route.targetServerName();
        String nextTarget = nextRoute == null ? null : nextRoute.targetServerName();
        boolean routeChanged = !Objects.equals(oldRevision, nextRevision);
        boolean preserveTestingMoves = routeChanged && "main".equals(oldTarget)
                && "main".equals(nextTarget) && nextRevision != null
                && nextRevision.startsWith("warmup:");
        boolean temporaryEventOpen = "main:manual".equals(nextRevision)
                || (nextRevision != null && nextRevision.startsWith("warmup:"));
        route = nextRoute;
        if (routeChanged && "main".equals(oldTarget)
                && "surprising-saturday".equals(nextTarget)) {
            waiting.replaceAll((uuid, entry) -> "main".equals(entry.destination())
                    ? new QueueEntry(entry.member(), entry.order(), "surprising-saturday") : entry);
            visitors.replaceAll((uuid, entry) -> "main".equals(entry.destination())
                    ? new QueueEntry(entry.member(), entry.order(), "surprising-saturday") : entry);
            shownPositions.clear();
        }
        if (nextRoute != null && !nextRoute.eventOpen() &&
                (hasWaiting("surprising-saturday") || visitors.values().stream().anyMatch(entry ->
                        "surprising-saturday".equals(entry.destination())))) {
            waiting.replaceAll((uuid, entry) -> "surprising-saturday".equals(entry.destination())
                    && !(temporaryEventOpen && "surprising-saturday".equals(temporaryDestinations.get(uuid)))
                    ? new QueueEntry(entry.member(), entry.order(), "main") : entry);
            visitors.replaceAll((uuid, entry) -> "surprising-saturday".equals(entry.destination())
                    && !(temporaryEventOpen && "surprising-saturday".equals(temporaryDestinations.get(uuid)))
                    ? new QueueEntry(entry.member(), entry.order(), "main") : entry);
            shownPositions.clear();
        }
        if ((routeChanged && !preserveTestingMoves)
                || ("surprising-saturday".equals(oldTarget) && "main".equals(nextTarget))) {
            manualDestinations.clear();
            temporaryDestinations.clear();
        }
        if (routeChanged || (wasInMaintenance && !maintenanceMode)
                || ("surprising-saturday".equals(oldTarget) && "main".equals(nextTarget))) {
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
        if (serverOnline("surprising-saturday")) {
            for (UUID uuid : new HashSet<>(pendingEventAdmins)) {
                Player player = proxy.getPlayer(uuid).orElse(null);
                if (player == null) {
                    pendingEventAdmins.remove(uuid);
                    continue;
                }
                if (!pendingEventAdmins.remove(uuid)) continue;
                RegisteredServer target = targetFor(player);
                if (target == null || !"surprising-saturday".equals(target.getServerInfo().getName())) {
                    continue;
                }
                player.createConnectionRequest(target).connect().thenAccept(result -> {
                    if (!result.isSuccessful() && proxy.getPlayer(uuid).isPresent()) pendingEventAdmins.add(uuid);
                });
            }
        }
    }

    private synchronized boolean admit(Player player, CapacityRole role, String destination) {
        UUID uuid = player.getUniqueId();
        String target = availableDestination(destination);
        if (!role.exempt() && (hasWaiting(target) || pendingAdmissions.containsKey(target)
                || occupied(target) >= limit(target)
                || !serverOnline(target))) {
            if (!serverOnline("waiting-room") || waitingRoomPopulation() >= WAITING_ROOM_LIMIT) return false;
            waiting.put(uuid, new QueueEntry(role.member(), queueOrder.incrementAndGet(), target));
        } else {
            admissions.add(uuid);
            admissionDestinations.put(uuid, target);
        }
        capacityRoles.put(uuid, role);
        return true;
    }

    private String availableDestination(String preferred) {
        boolean eventOpen = route != null && route.eventOpen();
        if ("surprising-saturday".equals(preferred) && eventOpen) return "surprising-saturday";
        if (!serverOnline("main") && eventOpen && serverOnline("surprising-saturday"))
            return "surprising-saturday";
        return "main";
    }

    private String availableDestination(UUID uuid, String preferred) {
        if ("surprising-saturday".equals(preferred)
                && "surprising-saturday".equals(temporaryDestinations.get(uuid))) return preferred;
        return availableDestination(preferred);
    }

    private int limit(String destination) {
        return maxPlayers.getOrDefault(destination, 42);
    }

    private boolean hasWaiting(String destination) {
        return waiting.values().stream().anyMatch(entry -> entry.destination().equals(destination));
    }

    private long occupied(String destination) {
        Set<UUID> counted = new HashSet<>();
        admissionDestinations.forEach((uuid, target) -> {
            if (destination.equals(target) && !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt())
                counted.add(uuid);
        });
        for (Player player : proxy.getAllPlayers()) {
            UUID uuid = player.getUniqueId();
            if (!capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt()
                    && player.getCurrentServer().stream().anyMatch(connection ->
                    destination.equals(connection.getServerInfo().getName()))) counted.add(uuid);
        }
        return counted.size();
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
        Set<UUID> selected = new HashSet<>();
        long now = System.currentTimeMillis();
        for (String destination : List.of("main", "surprising-saturday")) {
            long count = occupied(destination);
            int needed = (int) Math.max(0, count - limit(destination));
            if (hasWaiting(destination) && count >= limit(destination)) needed++;
            List<UUID> victims = admissions.stream()
                    .filter(uuid -> destination.equals(admissionDestinations.get(uuid)))
                    .filter(uuid -> !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt())
                    .filter(uuid -> joinedAt.containsKey(uuid))
                    .filter(uuid -> now - joinedAt.get(uuid) >= minimumPlaytime(uuid))
                    .sorted(Comparator.<UUID>comparingLong(uuid ->
                            now - joinedAt.get(uuid) - minimumPlaytime(uuid)).reversed()
                            .thenComparing(UUID::toString))
                    .limit(needed)
                    .toList();
            selected.addAll(victims);
        }
        for (UUID uuid : selected) {
            Player victim = proxy.getPlayer(uuid).orElse(null);
            if (victim == null) continue;
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
        for (String destination : List.of("main", "surprising-saturday")) {
            if (!serverOnline(destination) || "surprising-saturday".equals(destination)
                    && (route == null || !route.eventOpen())
                    && waiting.keySet().stream().noneMatch(uuid ->
                    destination.equals(temporaryDestinations.get(uuid)))) continue;
            while (!pendingAdmissions.containsKey(destination) && occupied(destination) < limit(destination)
                    && hasWaiting(destination)) {
                Map.Entry<UUID, QueueEntry> next = nextQueued(destination);
                if (next == null) break;
                UUID uuid = next.getKey();
                Player player = proxy.getPlayer(uuid).orElse(null);
                if (player == null) { waiting.remove(uuid); continue; }
                if (player.getCurrentServer().stream().noneMatch(connection ->
                        "waiting-room".equals(connection.getServerInfo().getName()))) break;
                boolean bothQueues = waiting.values().stream().anyMatch(entry ->
                        entry.destination().equals(destination) && entry.member())
                        && waiting.values().stream().anyMatch(entry ->
                        entry.destination().equals(destination) && !entry.member());
                RegisteredServer target = managedServers.get(destination);
                if (target == null) break;
                waiting.remove(uuid);
                shownPositions.remove(uuid);
                admissions.add(uuid);
                admissionDestinations.put(uuid, destination);
                pendingAdmissions.put(destination, uuid);
                manualDestinations.put(uuid, destination);
                admissionTurns.put(destination, bothQueues
                        ? (admissionTurns.getOrDefault(destination, 0) + 1) % 3 : 0);
                player.createConnectionRequest(target).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        manualDestinations.remove(uuid, destination);
                    } else {
                        synchronized (this) {
                            pendingAdmissions.remove(destination, uuid);
                            admissions.remove(uuid);
                            admissionDestinations.remove(uuid, destination);
                            manualDestinations.remove(uuid, destination);
                            if (proxy.getPlayer(uuid).isPresent()) waiting.put(uuid, next.getValue());
                        }
                    }
                });
            }
        }
    }

    private Map.Entry<UUID, QueueEntry> nextQueued(String destination) {
        long memberOrder = waiting.values().stream().filter(entry -> entry.destination().equals(destination)
                && entry.member()).mapToLong(QueueEntry::order).min().orElse(Long.MAX_VALUE);
        long normalOrder = waiting.values().stream().filter(entry -> entry.destination().equals(destination)
                && !entry.member()).mapToLong(QueueEntry::order).min().orElse(Long.MAX_VALUE);
        boolean member = QueuePolicy.chooseMember(memberOrder, normalOrder,
                admissionTurns.getOrDefault(destination, 0));
        return waiting.entrySet().stream()
                .filter(entry -> entry.getValue().destination().equals(destination)
                        && entry.getValue().member() == member)
                .min(Comparator.comparingLong(entry -> entry.getValue().order())).orElse(null);
    }

    private void showWaitingPositions() {
        for (String destination : List.of("main", "surprising-saturday")) {
            for (boolean members : List.of(true, false)) {
                List<Map.Entry<UUID, QueueEntry>> queue = waiting.entrySet().stream()
                        .filter(entry -> entry.getValue().member() == members
                                && entry.getValue().destination().equals(destination))
                        .sorted(Comparator.comparingLong(entry -> entry.getValue().order()))
                        .toList();
                for (int i = 0; i < queue.size(); i++) {
                    UUID uuid = queue.get(i).getKey();
                    Player player = proxy.getPlayer(uuid).orElse(null);
                    if (player == null) continue;
                    String label = (members ? "Member " : "")
                            + ("main".equals(destination) ? "server queue" : "event queue");
                    Component status = Component.text(label + ": position " + (i + 1));
                    player.sendActionBar(status);
                    String position = label + ":" + (i + 1);
                    String previous = shownPositions.put(uuid, position);
                    if (!position.equals(previous)) player.sendMessage(status);
                }
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
            if (backend.maxPlayers() != null && backend.maxPlayers() >= 0
                    && backend.maxPlayers() <= 99) maxPlayers.put(backend.name(), backend.maxPlayers());
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
                player.getCurrentServer().stream().map(connection -> {
                    String serverName = connection.getServerInfo().getName();
                    UUID uuid = player.getUniqueId();
                    QueueEntry queued = waiting.get(uuid);
                    QueueEntry visiting = visitors.get(uuid);
                    boolean inRoom = "waiting-room".equals(serverName);
                    return new ApiClient.OnlinePlayer(
                            uuid.toString(), sessionIds.get(uuid), player.getUsername(), serverName,
                            inRoom ? visiting != null ? "visiting" : "queued" : "playing",
                            inRoom ? queued != null ? queued.destination()
                                    : visiting != null ? visiting.destination()
                                    : admissionDestinations.getOrDefault(uuid, "main") : serverName,
                            temporaryDestinations.containsKey(uuid)
                    );
                })
        ).toList();
    }

    private synchronized void execute(ApiClient.MoveCommand command) {
        Optional<Player> player = parseUuid(command.playerUuid()).flatMap(proxy::getPlayer);
        if (player.isEmpty()) {
            acknowledgedCommands.add(command.id());
            return;
        }

        Player targetPlayer = player.get();
        UUID uuid = targetPlayer.getUniqueId();
        if (!Objects.equals(command.sessionId(), sessionIds.get(uuid))) {
            acknowledgedCommands.add(command.id());
            return;
        }
        if (pendingEventAdmins.contains(uuid) && "surprising-saturday".equals(command.targetServerName())) return;
        pendingEventAdmins.remove(uuid);
        if (connectingCommands.contains(command.id())) return;
        if (waiting.containsKey(uuid)) {
            if (command.temporary()) temporaryDestinations.put(uuid, command.targetServerName());
            else temporaryDestinations.remove(uuid);
            waiting.computeIfPresent(uuid, (id, entry) -> new QueueEntry(entry.member(),
                    queueOrder.incrementAndGet(), availableDestination(uuid, command.targetServerName())));
            shownPositions.remove(uuid);
            acknowledgedCommands.add(command.id());
            return;
        }
        if (visitors.containsKey(uuid)) {
            if (command.temporary()) temporaryDestinations.put(uuid, command.targetServerName());
            else temporaryDestinations.remove(uuid);
            visitors.computeIfPresent(uuid, (id, entry) -> new QueueEntry(entry.member(),
                    entry.order(), availableDestination(uuid, command.targetServerName())));
            acknowledgedCommands.add(command.id());
            return;
        }
        if (!serverOnline(command.targetServerName())) return;
        if (command.temporary()) temporaryDestinations.put(uuid, command.targetServerName());
        else temporaryDestinations.remove(uuid);
        manualDestinations.put(uuid, command.targetServerName());
        RegisteredServer target = targetFor(targetPlayer);
        if (target == null) return;
        if (command.targetServerName().equals(target.getServerInfo().getName())
                && targetPlayer.getCurrentServer()
                .map(connection -> connection.getServerInfo().equals(target.getServerInfo()))
                .orElse(false)) {
            acknowledgedCommands.add(command.id());
            return;
        }

        if (!connectingCommands.add(command.id())) return;
        targetPlayer.createConnectionRequest(target).connect().whenComplete((result, error) -> {
            if (error == null && result != null && result.isSuccessful()) {
                acknowledgedCommands.add(command.id());
            } else {
                logger.warn("Move of {} to {} failed: {}", targetPlayer.getUsername(),
                        command.targetServerName(), error == null ? result : error.toString());
                if ("surprising-saturday".equals(command.targetServerName())
                        && capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt()) {
                    pendingEventAdmins.add(uuid);
                } else {
                    manualDestinations.remove(uuid, command.targetServerName());
                    temporaryDestinations.remove(uuid, command.targetServerName());
                }
                restoreCurrentAdmission(targetPlayer);
            }
            connectingCommands.remove(command.id());
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
            player.createConnectionRequest(target).connect().thenAccept(result -> {
                if (!result.isSuccessful()) restoreCurrentAdmission(player);
            });
        }
    }

    private synchronized RegisteredServer targetFor(Player player) {
        UUID uuid = player.getUniqueId();
        if (maintenanceMode && !capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt()) return null;
        if (waiting.containsKey(uuid) || visitors.containsKey(uuid)) return managedServers.get("waiting-room");
        String name = temporaryDestinations.getOrDefault(uuid, manualDestinations.get(uuid));
        if (name == null && player.getCurrentServer().isEmpty())
            name = loginDestinations.getOrDefault(uuid, "main");
        if (name == null && route != null) name = route.targetServerName();
        name = availableDestination(uuid, name);
        if ("surprising-saturday".equals(name) && pendingEventAdmins.contains(uuid)
                && serverOnline("main")) return managedServers.get("main");
        if (!capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt()
                && !name.equals(admissionDestinations.get(uuid))) {
            if (hasWaiting(name) || pendingAdmissions.containsKey(name)
                    || occupied(name) >= limit(name) || !serverOnline(name)) {
                if (!serverOnline("waiting-room") || waitingRoomPopulation() >= WAITING_ROOM_LIMIT)
                    return player.getCurrentServer().map(connection -> {
                        String current = connection.getServerInfo().getName();
                        return "surprising-saturday".equals(current)
                                && !current.equals(availableDestination(uuid, current))
                                ? null : managedServers.get(current);
                    }).orElse(null);
                waiting.put(uuid, new QueueEntry(capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).member(),
                        queueOrder.incrementAndGet(), name));
                admissions.remove(uuid);
                admissionDestinations.remove(uuid);
                return managedServers.get("waiting-room");
            }
            admissions.add(uuid);
            admissionDestinations.put(uuid, name);
        }
        if (!serverOnline(name)) {
            if ("surprising-saturday".equals(name)
                    && capacityRoles.getOrDefault(uuid, CapacityRole.LOWEST).exempt()
                    && serverOnline("main")) {
                pendingEventAdmins.add(uuid);
                return managedServers.get("main");
            }
            return null;
        }
        return managedServers.get(name);
    }

    private synchronized void restoreCurrentAdmission(Player player) {
        UUID uuid = player.getUniqueId();
        waiting.remove(uuid);
        pendingAdmissions.values().removeIf(uuid::equals);
        String current = player.getCurrentServer().map(connection ->
                connection.getServerInfo().getName()).orElse(null);
        if ("main".equals(current) || "surprising-saturday".equals(current)) {
            admissions.add(uuid);
            admissionDestinations.put(uuid, current);
        } else {
            admissions.remove(uuid);
            admissionDestinations.remove(uuid);
        }
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
