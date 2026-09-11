package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons.PlayerAdvancementsAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsSync {
    private static final long BASE_SYNC_INTERVAL_TICKS = 60L * 20L;
    private static final long STAGGER_WINDOW_TICKS = 15L * 20L;
    private static final long SYNC_RETRY_TICKS = 5L * 20L;
    private static final String PROFILE_OBJECTIVE = "mmu_profile";
    private static final String PING_OBJECTIVE = "mmu_ping";
    private static final Map<UUID, Long> nextSyncTickByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<SyncPlayerStatsResponse>> activeSyncByPlayer = new ConcurrentHashMap<>();
    private static final Set<UUID> joinRefreshPending = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Boolean> membershipByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncPlayerStatsResponse> presentationByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, String> renderedProfileByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> colorByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> presentationRevisionByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> previousLastPlayedAtByPlayer = new ConcurrentHashMap<>();
    private static final List<Block> NOTABLE_MINED_BLOCKS = List.of(
            Blocks.STONE,
            Blocks.DEEPSLATE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE
    );

    private static long serverTicks;
    private static boolean sundayRewardDay = AdvancementMoney.isSundayRewardDay();

    private PlayerStatsSync() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            applyColor(handler.player, colorFor(handler.player));
            refreshPlayerList(server);
            joinRefreshPending.add(handler.player.getUUID());
            scheduleRetry(handler.player);
            syncNow(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            syncNow(handler.player, true);
            clearPlayer(handler.player.getUUID());
            refreshPlayerList(server);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            applyColor(newPlayer, colorFor(newPlayer));
            renderedProfileByPlayer.remove(newPlayer.getUUID());
            updateBelowName(newPlayer);
        });
    }

    public static void onServerTick(MinecraftServer server) {
        serverTicks++;

        if (serverTicks % (20L * 60L) == 0L) {
            boolean currentSundayRewardDay = AdvancementMoney.isSundayRewardDay();
            if (currentSundayRewardDay != sundayRewardDay) {
                sundayRewardDay = currentSundayRewardDay;
                server.getPlayerList().getPlayers().forEach(PlayerStatsSync::refreshAdvancementTooltips);
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (serverTicks % 20L == 0L) {
                updateBelowName(player);
                updatePlayerListPing(player);
            }

            if (serverTicks % (20L * 30L) == 0L) {
                applyColor(player, colorFor(player));
            }

            UUID playerId = player.getUUID();
            long nextSyncTick = nextSyncTickByPlayer.computeIfAbsent(
                    playerId,
                    id -> serverTicks + staggerTicks(player)
            );

            if (serverTicks < nextSyncTick) {
                continue;
            }

            // Keep a short retry scheduled until a successful response replaces it with the
            // normal interval. In particular, a failed first-join request must not leave the
            // player with stale presentation colors until the next normal sync.
            scheduleRetry(player);
            syncNow(player);
        }
    }

    private static void scheduleRetry(ServerPlayer player) {
        nextSyncTickByPlayer.put(player.getUUID(), serverTicks + SYNC_RETRY_TICKS);
    }

    private static void scheduleNext(ServerPlayer player) {
        nextSyncTickByPlayer.put(
                player.getUUID(),
                serverTicks + BASE_SYNC_INTERVAL_TICKS + staggerTicks(player)
        );
    }

    private static long staggerTicks(ServerPlayer player) {
        return Math.floorMod(player.getUUID().hashCode(), STAGGER_WINDOW_TICKS);
    }

    public static CompletableFuture<Boolean> syncNow(ServerPlayer player) {
        return syncNow(player, false);
    }

    public static boolean isMember(ServerPlayer player) {
        return player != null && membershipByPlayer.getOrDefault(player.getUUID(), false);
    }

    public static long previousLastPlayedAtUnixMs(ServerPlayer player) {
        return previousLastPlayedAtByPlayer.getOrDefault(player.getUUID(), 0L);
    }

    private static CompletableFuture<Boolean> syncNow(ServerPlayer player, boolean allowDisconnectedPlayer) {
        if (player == null || (!allowDisconnectedPlayer && player.hasDisconnected())) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<SyncPlayerStatsResponse> profileSync = allowDisconnectedPlayer
                ? GameplayGrpcService.syncPlayerStats(player, collectStats(player))
                : synchronizePresentation(player);
        CompletableFuture<Boolean> membershipSync = profileSync.thenApply(response -> {
            if (!allowDisconnectedPlayer) {
                previousLastPlayedAtByPlayer.put(player.getUUID(), response.getPreviousLastPlayedAtUnixMs());
            }
            return allowDisconnectedPlayer ? response.getAccountLinked() && response.getIsMember() : isMember(player);
        });

        if (allowDisconnectedPlayer) {
            profileSync.exceptionally(error -> {
                MainMod.LOGGER.debug("Failed to sync departing player stats for {}", player.getName().getString(), error);
                return null;
            });
        }

        return membershipSync.exceptionally(error -> isMember(player));
    }

    private static CompletableFuture<SyncPlayerStatsResponse> synchronizePresentation(ServerPlayer player) {
        UUID playerId = player.getUUID();
        CompletableFuture<SyncPlayerStatsResponse> current = activeSyncByPlayer.get(playerId);
        if (current != null) return current;

        long revision = presentationRevisionByPlayer.getOrDefault(playerId, 0L);
        MinecraftServer server = player.level().getServer();
        CompletableFuture<SyncPlayerStatsResponse> applied = new CompletableFuture<>();
        activeSyncByPlayer.put(playerId, applied);
        GameplayGrpcService.syncPlayerStats(player, collectStats(player)).whenComplete((response, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
            if (!activeSyncByPlayer.remove(playerId, applied)
                    || onlinePlayer == null || onlinePlayer.connection != player.connection) {
                applied.cancel(false);
                return;
            }
            if (error != null) {
                scheduleRetry(onlinePlayer);
                applied.completeExceptionally(error);
                MainMod.LOGGER.warn("Failed to sync player presentation for {}", player.getScoreboardName(), error);
                return;
            }
            try {
                // A website push that arrived during this request owns the newer presentation.
                boolean superseded = revision != presentationRevisionByPlayer.getOrDefault(playerId, 0L);
                if (!superseded) updatePresentation(onlinePlayer, response);
                if (joinRefreshPending.remove(playerId) || superseded) {
                    scheduleRetry(onlinePlayer);
                } else {
                    scheduleNext(onlinePlayer);
                }
                // Join listeners must see the applied profile, not just a completed network request.
                applied.complete(response);
            } catch (RuntimeException failure) {
                scheduleRetry(onlinePlayer);
                applied.completeExceptionally(failure);
                MainMod.LOGGER.error("Failed to apply player presentation for {}", player.getScoreboardName(), failure);
            }
        }));
        return applied;
    }

    private static void updatePresentation(ServerPlayer player, SyncPlayerStatsResponse response) {
        if (player.hasDisconnected()) return;

        boolean isMember = response.getAccountLinked() && response.getIsMember();
        Boolean previous = membershipByPlayer.put(player.getUUID(), isMember);
        presentationByPlayer.put(player.getUUID(), response);
        int color = PlayerColors.parse(response.getColorHex());
        updateTeam(player, response);
        applyColor(player, color);
        ClaimsManager.updateOwnerColor(player.getUUID(), color);
        renderedProfileByPlayer.remove(player.getUUID());
        updateBelowName(player);

        if (previous == null || previous != isMember) {
            refreshAdvancementTooltips(player);
        }
    }

    public static int colorFor(net.minecraft.world.entity.player.Player player) {
        Integer color = colorByPlayer.get(player.getUUID());
        if (color != null) return color;
        PlayerTeam team = player.getTeam();
        if (team != null && team.getName().equals(teamName(player.getUUID()))) {
            var savedColor = team.getDisplayName().getStyle().getColor();
            if (savedColor != null) return savedColor.getValue();
        }
        return PlayerColors.defaultColor(player.getUUID());
    }

    public static DiscordPresentation discordPresentation(ServerPlayer player) {
        SyncPlayerStatsResponse response = presentationByPlayer.get(player.getUUID());
        if (response == null) return new DiscordPresentation("Player", "", "", "#E6E6E6");
        String role = response.getAccountLinked() && response.getIsCommittee() ? "Committee"
                : response.getAccountLinked() && response.getIsMember() ? "Member"
                : response.getAccountLinked() && response.getIsExternal() ? "External" : "Player";
        return new DiscordPresentation(role, response.getNickname(), response.getPronouns(), response.getColorHex());
    }

    private static void clearPlayer(UUID playerId) {
        nextSyncTickByPlayer.remove(playerId);
        activeSyncByPlayer.remove(playerId);
        joinRefreshPending.remove(playerId);
        membershipByPlayer.remove(playerId);
        presentationByPlayer.remove(playerId);
        renderedProfileByPlayer.remove(playerId);
        colorByPlayer.remove(playerId);
        presentationRevisionByPlayer.remove(playerId);
        previousLastPlayedAtByPlayer.remove(playerId);
    }

    public record DiscordPresentation(String role, String nickname, String pronouns, String colorHex) { }

    public static void applyColor(ServerPlayer player, int color) {
        color = PlayerColors.withMinimumLightness(color);
        colorByPlayer.put(player.getUUID(), color);
        var waypoints = player.level().getWaypointManager();
        waypoints.untrackWaypoint(player);
        player.waypointIcon().color = Optional.of(color);
        waypoints.trackWaypoint(player);
        PlayerTeam team = playerTeam(player);
        team.setDisplayName(Component.literal(player.getScoreboardName()).withColor(color));
        team.setColor(Optional.of(PlayerColors.closestTeamColor(color)));
        player.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player
        ));
    }

    private static void refreshPlayerList(MinecraftServer server) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                server.getPlayerList().getPlayers()
        ));
    }

    public static void applyWebsiteColor(ServerPlayer player, int color) {
        presentationRevisionByPlayer.merge(player.getUUID(), 1L, Long::sum);
        SyncPlayerStatsResponse presentation = presentationByPlayer.get(player.getUUID());
        if (presentation != null) {
            presentationByPlayer.put(player.getUUID(), presentation.toBuilder()
                    .setColorHex(String.format(Locale.ROOT, "#%06X", color))
                    .build());
        }
        applyColor(player, color);
    }

    public static void applyShowDeathCounter(ServerPlayer player, boolean showDeathCounter) {
        SyncPlayerStatsResponse presentation = presentationByPlayer.get(player.getUUID());
        if (presentation == null) return;
        presentationRevisionByPlayer.merge(player.getUUID(), 1L, Long::sum);
        presentationByPlayer.put(player.getUUID(), presentation.toBuilder()
                .setShowDeathCounter(showDeathCounter)
                .build());
        renderedProfileByPlayer.remove(player.getUUID());
        updateBelowName(player);
    }

    public static void applyPresentation(
            ServerPlayer player,
            String nickname,
            String pronouns,
            String colorHex,
            boolean showDeathCounter,
            boolean isMember,
            boolean isCommittee,
            boolean isExternal
    ) {
        presentationRevisionByPlayer.merge(player.getUUID(), 1L, Long::sum);
        SyncPlayerStatsResponse response = SyncPlayerStatsResponse.newBuilder()
                .setAccepted(true)
                .setAccountLinked(true)
                .setIsMember(isMember)
                .setIsCommittee(isCommittee)
                .setIsExternal(isExternal)
                .setNickname(nickname)
                .setPronouns(pronouns)
                .setColorHex(colorHex)
                .setShowDeathCounter(showDeathCounter)
                .setMessage("Profile updated from website.")
                .build();
        updatePresentation(player, response);
    }

    private static String teamName(UUID playerId) {
        return "mmu" + playerId.toString().replace("-", "").substring(0, 13);
    }

    private static PlayerTeam playerTeam(ServerPlayer player) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        String playerName = player.getScoreboardName();
        String teamName = teamName(player.getUUID());
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        if (player.getTeam() != team) {
            scoreboard.addPlayerToTeam(playerName, team);
        }
        return team;
    }

    private static void updateTeam(ServerPlayer player, SyncPlayerStatsResponse response) {
        PlayerTeam team = playerTeam(player);
        String label = response.getAccountLinked() && response.getIsCommittee() ? " [Committee]"
                : response.getAccountLinked() && response.getIsExternal() ? " [External]"
                : response.getAccountLinked() && response.getIsMember() ? " [Member]" : "";
        ChatFormatting labelColor = response.getIsCommittee() ? ChatFormatting.AQUA
                : response.getIsExternal() ? ChatFormatting.GRAY : ChatFormatting.GREEN;
        team.setPlayerSuffix(Component.literal(label).withStyle(labelColor));
    }

    private static void updateBelowName(ServerPlayer player) {
        SyncPlayerStatsResponse presentation = presentationByPlayer.get(player.getUUID());
        if (presentation == null) return;

        ServerStatsCounter stats = player.getStats();
        long dangerCount = (long) stats.getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING))
                + stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        String nickname = presentation.getNickname().trim();
        String pronouns = presentation.getPronouns().trim();
        String prefix = nickname.isEmpty() ? pronouns : pronouns.isEmpty() ? nickname : nickname + " - " + pronouns;
        boolean showDeathCounter = !presentation.hasShowDeathCounter() || presentation.getShowDeathCounter();
        String dangerText = showDeathCounter ? dangerCount + "☠" : "";
        String text = prefix + (prefix.isEmpty() || dangerText.isEmpty() ? "" : " - ") + dangerText;
        if (text.equals(renderedProfileByPlayer.put(player.getUUID(), text))) return;

        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(PROFILE_OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    PROFILE_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Component.empty(),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }
        if (scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME) != objective) {
            scoreboard.setDisplayObjective(DisplaySlot.BELOW_NAME, objective);
        }

        ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, objective);
        score.set(0);
        score.numberFormatOverride(new FixedFormat(Component.literal(text)));
    }

    private static void updatePlayerListPing(ServerPlayer player) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(PING_OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    PING_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Ping"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }
        if (scoreboard.getDisplayObjective(DisplaySlot.LIST) != objective) {
            scoreboard.setDisplayObjective(DisplaySlot.LIST, objective);
        }

        int latency = player.connection.latency();
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, objective);
        score.set(latency);
        score.numberFormatOverride(new FixedFormat(Component.literal(latency + " ms")));
    }

    private static void refreshAdvancementTooltips(ServerPlayer player) {
        if (player.hasDisconnected()) {
            return;
        }

        PlayerAdvancements advancements = player.getAdvancements();
        PlayerAdvancementsAccessor accessor = (PlayerAdvancementsAccessor) advancements;
        advancements.visible.clear();
        for (AdvancementNode root : advancements.tree.roots()) {
            accessor.mainmod$getRootsToUpdate().add(root);
        }
        accessor.mainmod$setFirstPacket(true);
        advancements.flushDirty(player, true);
    }

    private static List<MinecraftStatEntry> collectStats(ServerPlayer player) {
        ServerStatsCounter stats = player.getStats();
        List<MinecraftStatEntry> entries = new ArrayList<>();
        StatType<Identifier> customStats = Stats.CUSTOM;

        // CUSTOM_STAT is a Registry<Identifier>; StatType wants the registered values, not the registry keys.
        BuiltInRegistries.CUSTOM_STAT.stream().forEach(statId -> addStat(
                entries,
                "custom",
                statId,
                customLabel(statId),
                stats.getValue(customStats, statId)
        ));

        for (Block block : NOTABLE_MINED_BLOCKS) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            addStat(
                    entries,
                    "mined",
                    blockId,
                    humanize(blockId) + " Mined",
                    stats.getValue(Stats.BLOCK_MINED, block)
            );
        }

        PlayerAdvancements advancements = player.getAdvancements();
        List<AdvancementNode> visibleAdvancements = advancements.tree.nodes().stream()
                .filter(node -> node.holder().value().display().isPresent())
                .toList();
        long completedAdvancements = visibleAdvancements.stream()
                .map(AdvancementNode::holder)
                .filter(holder -> advancements.getOrStartProgress(holder).isDone())
                .count();
        entries.add(MinecraftStatEntry.newBuilder()
                .setKey("minecraft.advancement.minecraft:earned")
                .setCategory("advancement")
                .setId("minecraft:earned")
                .setLabel("Advancements Earned")
                .setValue(completedAdvancements)
                .setTotal(visibleAdvancements.size())
                .build());

        for (Identifier entityId : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
            addStat(
                    entries,
                    "killed",
                    entityId,
                    humanize(entityId) + " Killed",
                    stats.getValue(Stats.ENTITY_KILLED, entityType)
            );
            addStat(
                    entries,
                    "killed_by",
                    entityId,
                    "Killed By " + humanize(entityId),
                    stats.getValue(Stats.ENTITY_KILLED_BY, entityType)
            );
        }

        return entries;
    }

    private static void addStat(
            List<MinecraftStatEntry> entries,
            String category,
            Identifier id,
            String label,
            int value
    ) {
        entries.add(MinecraftStatEntry.newBuilder()
                .setKey("minecraft." + category + "." + id)
                .setCategory(category)
                .setId(id.toString())
                .setLabel(label)
                .setValue(Math.max(0, value))
                .build());
    }

    private static String customLabel(Identifier id) {
        return switch (id.toString()) {
            case "minecraft:play_time" -> "Play Time";
            case "minecraft:total_world_time" -> "Total World Time";
            case "minecraft:time_since_death" -> "Time Since Death";
            case "minecraft:time_since_rest" -> "Time Since Rest";
            case "minecraft:sneak_time", "minecraft:crouch_time" -> "Crouch Time";
            case "minecraft:walk_one_cm" -> "Distance Walked";
            case "minecraft:sprint_one_cm" -> "Distance Sprinted";
            case "minecraft:crouch_one_cm" -> "Distance Crouched";
            case "minecraft:fall_one_cm" -> "Distance Fallen";
            case "minecraft:fly_one_cm" -> "Distance Flown";
            case "minecraft:swim_one_cm" -> "Distance Swum";
            case "minecraft:boat_one_cm" -> "Distance By Boat";
            case "minecraft:minecart_one_cm" -> "Distance By Minecart";
            case "minecraft:horse_one_cm" -> "Distance By Horse";
            case "minecraft:aviate_one_cm" -> "Distance By Elytra";
            case "minecraft:jump" -> "Times Jumped";
            case "minecraft:drop" -> "Items Dropped";
            case "minecraft:damage_dealt" -> "Damage Dealt";
            case "minecraft:damage_taken" -> "Damage Taken";
            case "minecraft:deaths" -> "Deaths";
            case "minecraft:mob_kills" -> "Mob Kills";
            case "minecraft:player_kills" -> "Player Kills";
            case "minecraft:animals_bred" -> "Animals Bred";
            case "minecraft:fish_caught" -> "Fish Caught";
            case "minecraft:talked_to_villager" -> "Villagers Talked To";
            case "minecraft:traded_with_villager" -> "Trades With Villagers";
            case "minecraft:raid_trigger" -> "Raids Triggered";
            case "minecraft:raid_win" -> "Raids Won";
            default -> humanize(id);
        };
    }

    private static String humanize(Identifier id) {
        String path = id.getPath().replace('/', '_');
        String[] parts = path.split("_+");
        List<String> words = new ArrayList<>();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }

        return String.join(" ", words);
    }
}
