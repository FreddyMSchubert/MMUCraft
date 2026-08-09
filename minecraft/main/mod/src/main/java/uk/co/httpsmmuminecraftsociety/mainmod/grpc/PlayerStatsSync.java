package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons.PlayerAdvancementsAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsSync {
    private static final long BASE_SYNC_INTERVAL_TICKS = 20L * 60L * 20L;
    private static final long STAGGER_WINDOW_TICKS = 5L * 60L * 20L;
    private static final String PROFILE_OBJECTIVE = "mmu_profile";
    private static final Map<UUID, Long> nextSyncTickByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> membershipByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncPlayerStatsResponse> presentationByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, String> renderedProfileByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> colorByPlayer = new ConcurrentHashMap<>();

    private static long serverTicks;
    private static boolean sundayRewardDay = AdvancementMoney.isSundayRewardDay();

    private PlayerStatsSync() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncNow(handler.player);
            scheduleNext(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            syncNow(handler.player, true);
            server.execute(() -> clearPlayer(handler.player.getUUID()));
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
            }

            UUID playerId = player.getUUID();
            long nextSyncTick = nextSyncTickByPlayer.computeIfAbsent(
                    playerId,
                    id -> serverTicks + staggerTicks(player)
            );

            if (serverTicks < nextSyncTick) {
                continue;
            }

            syncNow(player);
            scheduleNext(player);
        }
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

    private static CompletableFuture<Boolean> syncNow(ServerPlayer player, boolean allowDisconnectedPlayer) {
        if (player == null || (!allowDisconnectedPlayer && player.hasDisconnected())) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<SyncPlayerStatsResponse> profileSync = GameplayGrpcService.syncPlayerStats(player, collectStats(player));
        CompletableFuture<Boolean> membershipSync = profileSync
                .thenApply(response -> response.getAccountLinked() && response.getIsMember());

        profileSync
                .thenAccept(response -> {
                    if (!allowDisconnectedPlayer && !player.hasDisconnected()) {
                        MinecraftServer server = player.level().getServer();
                        if (server != null) {
                            server.execute(() -> updatePresentation(player, response));
                        }
                    }
                })
                .exceptionally(error -> {
                    MainMod.LOGGER.debug("Failed to sync player stats for {}", player.getName().getString(), error);
                    return null;
                });

        return membershipSync.exceptionally(error -> isMember(player));
    }

    private static void updatePresentation(ServerPlayer player, SyncPlayerStatsResponse response) {
        if (player.hasDisconnected()) return;

        boolean isMember = response.getAccountLinked() && response.getIsMember();
        Boolean previous = membershipByPlayer.put(player.getUUID(), isMember);
        presentationByPlayer.put(player.getUUID(), response);
        int color = parseColor(response.getColorHex());
        applyColor(player, color);
        ClaimsManager.updateOwnerColor(player.getUUID(), color);
        renderedProfileByPlayer.remove(player.getUUID());
        updateTeam(player, response);
        updateBelowName(player);

        if (previous == null || previous != isMember) {
            refreshAdvancementTooltips(player);
        }
    }

    public static int colorFor(net.minecraft.world.entity.player.Player player) {
        return colorByPlayer.getOrDefault(player.getUUID(), -1);
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
        membershipByPlayer.remove(playerId);
        presentationByPlayer.remove(playerId);
        renderedProfileByPlayer.remove(playerId);
        colorByPlayer.remove(playerId);
    }

    public record DiscordPresentation(String role, String nickname, String pronouns, String colorHex) { }

    public static void applyColor(ServerPlayer player, int color) {
        if (Integer.valueOf(color).equals(colorByPlayer.put(player.getUUID(), color))) return;
        var waypoints = player.level().getWaypointManager();
        waypoints.untrackWaypoint(player);
        player.waypointIcon().color = Optional.of(color);
        waypoints.trackWaypoint(player);
        updateTeamColor(player, color);
    }

    private static int parseColor(String color) {
        if (color.length() != 7 || color.charAt(0) != '#') return 0xE6E6E6;
        try {
            return Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException ignored) {
            return 0xE6E6E6;
        }
    }

    private static void updateTeam(ServerPlayer player, SyncPlayerStatsResponse response) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        String playerName = player.getScoreboardName();
        String teamName = "mmu" + player.getUUID().toString().replace("-", "").substring(0, 13);
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(playerName);

        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        String label = response.getAccountLinked() && response.getIsCommittee() ? " [Committee]"
                : response.getAccountLinked() && response.getIsExternal() ? " [External]"
                : response.getAccountLinked() && response.getIsMember() ? " [Member]" : "";
        ChatFormatting labelColor = response.getIsCommittee() ? ChatFormatting.AQUA
                : response.getIsExternal() ? ChatFormatting.GRAY : ChatFormatting.GREEN;
        team.setPlayerSuffix(Component.literal(label).withStyle(labelColor));
        team.setColor(Optional.of(closestTeamColor(parseColor(response.getColorHex()))));
        if (currentTeam != team) {
            scoreboard.addPlayerToTeam(playerName, team);
        }
    }

    private static void updateTeamColor(ServerPlayer player, int color) {
        PlayerTeam team = player.getTeam();
        if (team != null && team.getName().startsWith("mmu")) {
            team.setColor(Optional.of(closestTeamColor(color)));
        }
    }

    private static TeamColor closestTeamColor(int rgb) {
        TeamColor closest = TeamColor.WHITE;
        int shortestDistance = Integer.MAX_VALUE;
        for (TeamColor candidate : TeamColor.VALUES) {
            int red = (rgb >> 16 & 0xFF) - (candidate.rgb() >> 16 & 0xFF);
            int green = (rgb >> 8 & 0xFF) - (candidate.rgb() >> 8 & 0xFF);
            int blue = (rgb & 0xFF) - (candidate.rgb() & 0xFF);
            int distance = red * red + green * green + blue * blue;
            if (distance < shortestDistance) {
                closest = candidate;
                shortestDistance = distance;
            }
        }
        return closest;
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
        String text = (prefix.isEmpty() ? "" : prefix + " - ") + dangerCount + "☠";
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

        long completedAdvancements = player.getAdvancements().visible.stream()
                .filter(holder -> holder.value().display().isPresent())
                .filter(holder -> player.getAdvancements().getOrStartProgress(holder).isDone())
                .count();
        addStat(
                entries,
                "advancement",
                Identifier.fromNamespaceAndPath("minecraft", "earned"),
                "Advancements Earned",
                (int) completedAdvancements
        );

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
