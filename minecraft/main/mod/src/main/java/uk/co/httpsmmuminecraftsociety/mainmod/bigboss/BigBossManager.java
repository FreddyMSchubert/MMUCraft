package uk.co.httpsmmuminecraftsociety.mainmod.bigboss;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossHeartbeatRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossKillRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossNoticeRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossPlayerRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossStateResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GrpcBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BigBossManager {
    public static final int COLOR = 0xB35CFF;
    public static final String KEEP_TAG = "mainmod_big_boss_keep_inventory";
    private static final URI PAGE = URI.create("https://mmuminecraftsociety.co.uk/play/big-boss");
    private static final int[] HOUR_MILESTONES = {1, 3, 5, 10, 15, 20, 25, 30, 35, 40, 42};
    private static final Map<UUID, Transfer> deathTransfers = new HashMap<>();
    private static String holder = "";
    private static long reignStarted;
    private static String sessionId = UUID.randomUUID().toString();
    private static long sessionQualifyingMs;
    private static long lastSampleMs;
    private static long lastChangeMs;
    private static long ticks;
    private static boolean heartbeatInFlight;

    private BigBossManager() {}

    public static boolean isBoss(ServerPlayer player) {
        return !holder.isEmpty() && holder.equalsIgnoreCase(player.getName().getString());
    }

    public static ServerPlayer onlineBoss(MinecraftServer server) {
        return holder.isEmpty() ? null : server.getPlayerList().getPlayerByName(holder);
    }

    public static void joined(ServerPlayer player) {
        long sentAt = System.currentTimeMillis();
        BlockPos pos = player.blockPosition();
        GameplayGrpcService.bigBossJoin(BigBossPlayerRequest.newBuilder()
                .setMinecraftUsername(player.getName().getString())
                .setDimension(player.level().dimension().identifier().toString())
                .setX(pos.getX()).setY(pos.getY()).setZ(pos.getZ())
                .setInClaim(ClaimsManager.isClaimed(player.level(), pos)).build()).whenComplete((state, error) ->
                GrpcBridge.runOnMainThread(() -> {
                    if (error == null && sentAt >= lastChangeMs) apply(state, player);
                    else MainMod.LOGGER.warn("Could not read Big Boss state on join", error);
                }));
    }

    public static void left(ServerPlayer player) {
        var flushed = isBoss(player) ? GameplayGrpcService.bigBossHeartbeat(BigBossHeartbeatRequest.newBuilder()
                .setHolderUsername(holder).setReignStartedAtUnixMs(reignStarted)
                .setSessionId(sessionId).setQualifyingMs(sessionQualifyingMs).setOnline(false).build())
                : java.util.concurrent.CompletableFuture.completedFuture(null);
        flushed.handle((ignored, error) -> null)
                .thenCompose(ignored -> GameplayGrpcService.bigBossLeave(player.getName().getString()))
                .exceptionally(error -> {
            MainMod.LOGGER.warn("Could not record Big Boss logout", error);
            return null;
        });
    }

    public static void tick(MinecraftServer server) {
        ticks++;
        ServerPlayer boss = onlineBoss(server);
        long now = System.currentTimeMillis();
        if (boss != null && lastSampleMs > 0 && !ClaimsManager.isClaimed(boss.level(), boss.blockPosition())) {
            sessionQualifyingMs += Math.max(0, Math.min(1_000, now - lastSampleMs));
        }
        lastSampleMs = now;
        if (ticks % 100 != 0 || heartbeatInFlight) return;
        BlockPos pos = boss == null ? BlockPos.ZERO : boss.blockPosition();
        BigBossHeartbeatRequest request = BigBossHeartbeatRequest.newBuilder()
                .setHolderUsername(holder).setReignStartedAtUnixMs(reignStarted)
                .setSessionId(sessionId).setQualifyingMs(sessionQualifyingMs)
                .setOnline(boss != null)
                .setDimension(boss == null ? "" : boss.level().dimension().identifier().toString())
                .setX(pos.getX()).setY(pos.getY()).setZ(pos.getZ())
                .setInClaim(boss != null && ClaimsManager.isClaimed(boss.level(), pos)).build();
        heartbeatInFlight = true;
        GameplayGrpcService.bigBossHeartbeat(request).whenComplete((state, error) ->
                GrpcBridge.runOnMainThread(() -> {
                    heartbeatInFlight = false;
                    if (error == null && now >= lastChangeMs) apply(state, boss);
                    else MainMod.LOGGER.warn("Could not update Big Boss position", error);
                }));
    }

    public static void notice(BigBossNoticeRequest notice) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) return;
        if (!notice.getPresenceOnly() && !notice.getNextUsername().isEmpty()) holder = notice.getNextUsername();
        else if (!notice.getPresenceOnly() && !notice.getPreviousUsername().isEmpty()) holder = "";
        if (!notice.getPresenceOnly() && (!notice.getPreviousUsername().isEmpty() || !notice.getNextUsername().isEmpty())) {
            lastChangeMs = System.currentTimeMillis();
            reignStarted = notice.getReignStartedAtUnixMs();
            sessionId = UUID.randomUUID().toString();
            sessionQualifyingMs = 0;
            lastSampleMs = 0;
        }
        PlayerStatsSync.refreshBossLabels(server);
        if (notice.getPreviousEarnedStop()) {
            ServerPlayer previous = server.getPlayerList().getPlayerByName(notice.getPreviousUsername());
            if (previous != null) MasteryAdvancements.grant(previous, "big_boss/stop");
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            String message = viewer.getName().getString().equalsIgnoreCase(notice.getNextUsername())
                    ? notice.getNextPrivateMessage()
                    : viewer.getName().getString().equalsIgnoreCase(notice.getPreviousUsername())
                    ? notice.getPreviousPrivateMessage() : notice.getMessage();
            if (!message.isBlank()) viewer.sendSystemMessage(linked(message));
        }
    }

    public static boolean killed(ServerPlayer victim, DamageSource source) {
        if (!isBoss(victim) || !(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return false;
        try {
            var response = GameplayGrpcService.recordBigBossKill(BigBossKillRequest.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setVictimUsername(victim.getName().getString())
                    .setKillerUsername(killer.getName().getString())
                    .setDeathMessage(victim.getCombatTracker().getDeathMessage().getString())
                    .setVictimSessionId(sessionId).setVictimQualifyingMs(sessionQualifyingMs)
                    .setVictimReignStartedAtUnixMs(reignStarted)
                    .build()).get(6, TimeUnit.SECONDS);
            if (!response.getTransferred()) return false;
            victim.addTag(KEEP_TAG);
            deathTransfers.put(victim.getUUID(), new Transfer(killer.getName().getString(),
                    response.getKillerWinCount(), response.getVictimHadKillWonReign(),
                    response.getVictimTotalQualifyingMs()));
            holder = response.getHolderUsername();
            lastChangeMs = System.currentTimeMillis();
            reignStarted = response.getReignStartedAtUnixMs();
            sessionId = UUID.randomUUID().toString();
            sessionQualifyingMs = 0;
            lastSampleMs = 0;
            PlayerStatsSync.refreshBossLabels(victim.level().getServer());
            if (!victim.level().getGameRules().get(GameRules.SHOW_DEATH_MESSAGES))
                announceDeath(victim, victim.getCombatTracker().getDeathMessage());
            return true;
        } catch (Exception error) {
            MainMod.LOGGER.warn("Could not transfer Big Boss title after a kill", error);
            return false;
        }
    }

    public static boolean announceDeath(ServerPlayer victim, Component deathMessage) {
        Transfer transfer = deathTransfers.remove(victim.getUUID());
        if (transfer == null) return false;
        String killerName = transfer.killerName();
        for (ServerPlayer viewer : victim.level().getServer().getPlayerList().getPlayers()) {
            String name = viewer.getName().getString();
            String text = name.equalsIgnoreCase(victim.getName().getString())
                    ? "You were killed by " + killerName + ". You lost the Big Boss title, but kept your items and experience."
                    : name.equalsIgnoreCase(killerName)
                    ? "You killed " + victim.getName().getString() + ". You are now the Big Boss."
                    : deathMessage.getString() + " " + killerName + " is now the Big Boss.";
            viewer.sendSystemMessage(linked(text));
        }
        ServerPlayer killer = victim.level().getServer().getPlayerList().getPlayerByName(killerName);
        if (killer != null) {
            MasteryAdvancements.grant(killer, "big_boss/become");
            if (transfer.winCount() >= 2) MasteryAdvancements.grant(killer, "big_boss/win_2");
            if (transfer.winCount() >= 5) MasteryAdvancements.grant(killer, "big_boss/win_5");
        }
        if (transfer.victimEarnedStop()) MasteryAdvancements.grant(victim, "big_boss/stop");
        rewardTime(victim, transfer.victimTotalQualifyingMs());
        return true;
    }

    public static Component linked(String text) {
        MutableComponent message = Component.literal("👑 ").withColor(COLOR);
        int start = 0;
        int match;
        while ((match = text.indexOf("Big Boss", start)) >= 0) {
            message.append(Component.literal(text.substring(start, match)).withColor(COLOR));
            message.append(Component.literal("Big Boss").withStyle(style -> style.withColor(COLOR)
                    .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(PAGE))));
            start = match + "Big Boss".length();
        }
        message.append(Component.literal(text.substring(start)).withColor(COLOR));
        if (start == 0) message.append(Component.literal(" [Big Boss]").withStyle(style -> style.withColor(COLOR)
                .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(PAGE))));
        return message;
    }

    private static void apply(BigBossStateResponse state, ServerPlayer progressPlayer) {
        boolean changed = !holder.equalsIgnoreCase(state.getHolderUsername())
                || reignStarted != state.getReignStartedAtUnixMs();
        holder = state.getHolderUsername();
        reignStarted = state.getReignStartedAtUnixMs();
        if (changed) {
            lastChangeMs = System.currentTimeMillis();
            sessionId = UUID.randomUUID().toString();
            sessionQualifyingMs = 0;
            lastSampleMs = 0;
            MinecraftServer server = GrpcBridge.minecraftServer();
            if (server != null) PlayerStatsSync.refreshBossLabels(server);
        }
        if (progressPlayer == null || progressPlayer.hasDisconnected()) return;
        if (state.getHasLostKillWonReign()) MasteryAdvancements.grant(progressPlayer, "big_boss/stop");
        rewardTime(progressPlayer, state.getQualifyingMs());
    }

    private static void rewardTime(ServerPlayer progressPlayer, long qualifyingMs) {
        int hours = (int) Math.min(Integer.MAX_VALUE, qualifyingMs / 3_600_000);
        for (int milestone : HOUR_MILESTONES) {
            if (hours >= milestone) MasteryAdvancements.grant(progressPlayer, "big_boss/hours_" + milestone);
        }
        ServerScoreboard scoreboard = progressPlayer.level().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective("mmu_boss_hours");
        if (objective == null) objective = scoreboard.addObjective("mmu_boss_hours", ObjectiveCriteria.DUMMY,
                Component.empty(), ObjectiveCriteria.RenderType.INTEGER, false, null);
        ScoreAccess paid = scoreboard.getOrCreatePlayerScore(progressPlayer, objective);
        int difference = hours - paid.get();
        if (difference > 0 && MoneyHelper.GainMoney(progressPlayer, difference * 5)) {
            paid.set(hours);
            MoneyHelper.SendBalanceMessage(progressPlayer, difference * 5,
                    "Big Boss time: " + hours + (hours == 1 ? " hour" : " hours"));
        }
    }

    private record Transfer(String killerName, int winCount, boolean victimEarnedStop,
                            long victimTotalQualifyingMs) {}
}
