package uk.co.httpsmmuminecraftsociety.mainmod.claims;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ClaimData;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ClaimsSnapshot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimsManager {
    private static final ThreadLocal<ArrayDeque<ServerPlayer>> ACTORS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> FIRE_TICKS = ThreadLocal.withInitial(() -> 0);
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static final Map<UUID, String> BOSS_BAR_STATES = new HashMap<>();
    private static final Map<UUID, Long> LAST_DENIED_MESSAGE = new HashMap<>();

    private static volatile Map<ClaimKey, Claim> claims = Map.of();
    private static volatile boolean ready;

    private ClaimsManager() {
    }

    public static void init() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                checkDirect(player, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                checkDirect(player, level, hit.getBlockPos()) ? InteractionResult.PASS : InteractionResult.FAIL);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                checkDirect(player, level, pos));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                checkDirect(player, level, entity.blockPosition()) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                checkDirect(player, level, entity.blockPosition()) ? InteractionResult.PASS : InteractionResult.FAIL);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeBossBar(handler.getPlayer()));
    }

    public static void reset() {
        claims = Map.of();
        ready = false;
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
        BOSS_BAR_STATES.clear();
        LAST_DENIED_MESSAGE.clear();
        ACTORS.remove();
        FIRE_TICKS.remove();
    }

    public static void apply(ClaimsSnapshot snapshot) {
        Map<ClaimKey, Claim> next = new HashMap<>();
        for (ClaimData data : snapshot.getClaimsList()) {
            try {
                Set<UUID> members = new HashSet<>();
                members.add(UUID.fromString(data.getOwnerUuid()));
                for (String memberUuid : data.getMemberUuidsList()) {
                    members.add(UUID.fromString(memberUuid));
                }
                next.put(
                        new ClaimKey(data.getDimension(), ChunkPos.pack(data.getChunkX(), data.getChunkZ())),
                        new Claim(data.getId(), UUID.fromString(data.getOwnerUuid()), data.getOwnerName(), Set.copyOf(members))
                );
            } catch (IllegalArgumentException ignored) {
                // A malformed UUID cannot be allowed to make all otherwise valid claims disappear.
            }
        }
        claims = Map.copyOf(next);
        ready = true;
        BOSS_BAR_STATES.clear();
    }

    public static void beginPlayerAction(ServerPlayer player) {
        ACTORS.get().push(player);
    }

    public static void endPlayerAction() {
        ArrayDeque<ServerPlayer> actors = ACTORS.get();
        if (!actors.isEmpty()) actors.pop();
        if (actors.isEmpty()) ACTORS.remove();
    }

    public static void beginFireTick() {
        FIRE_TICKS.set(FIRE_TICKS.get() + 1);
    }

    public static void endFireTick() {
        int depth = FIRE_TICKS.get() - 1;
        if (depth <= 0) FIRE_TICKS.remove();
        else FIRE_TICKS.set(depth);
    }

    public static boolean allowBlockMutation(Level level, BlockPos pos, BlockState newState) {
        ArrayDeque<ServerPlayer> actors = ACTORS.get();
        if (!actors.isEmpty()) return canAccess(actors.peek(), level, pos);

        if (FIRE_TICKS.get() > 0 && (!ready || isClaimed(level, pos))) {
            return level.getBlockState(pos).getBlock() instanceof BaseFireBlock;
        }
        if ((!ready || isClaimed(level, pos)) && newState.getBlock() instanceof BaseFireBlock) return false;
        return true;
    }

    public static boolean allowEntitySpawn(Level level, Entity entity) {
        ArrayDeque<ServerPlayer> actors = ACTORS.get();
        return actors.isEmpty() || canAccess(actors.peek(), level, entity.blockPosition());
    }

    public static boolean canAccess(ServerPlayer player, Level level, BlockPos pos) {
        if (isOperator(player)) return true;
        if (!ready) return false;
        Claim claim = claimAt(level, pos);
        return claim == null || claim.members().contains(player.getUUID());
    }

    public static boolean isClaimed(Level level, BlockPos pos) {
        return claimAt(level, pos) != null;
    }

    public static boolean isReady() {
        return ready;
    }

    public static String claimIdAt(Level level, BlockPos pos) {
        Claim claim = claimAt(level, pos);
        return claim == null ? null : claim.id();
    }

    public static BlockHitResult projectileBarrier(Projectile projectile, Vec3 start, Vec3 movement) {
        if (movement.lengthSqr() == 0) return null;
        if (!ready) {
            return new BlockHitResult(
                    start,
                    Direction.getApproximateNearest(movement).getOpposite(),
                    BlockPos.containing(start),
                    false
            );
        }
        Entity owner = projectile.getOwner();
        ServerPlayer player = owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player != null && isOperator(player)) return null;

        int steps = Math.max(1, (int) Math.ceil(movement.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.add(movement.scale((double) i / steps));
            BlockPos pos = BlockPos.containing(point);
            Claim claim = claimAt(projectile.level(), pos);
            if (claim != null && (player == null || !claim.members().contains(player.getUUID()))) {
                Direction face = Direction.getApproximateNearest(movement).getOpposite();
                return new BlockHitResult(point, face, pos, false);
            }
        }
        return null;
    }

    public static void tickBossBars(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerBossEvent existingBar = BOSS_BARS.get(player.getUUID());
            if (existingBar != null && !existingBar.getPlayers().contains(player)) {
                existingBar.removeAllPlayers();
                existingBar.addPlayer(player);
                BOSS_BAR_STATES.remove(player.getUUID());
            }

            Claim claim = claimAt(player.level(), player.blockPosition());
            String state = claim == null ? "" : claim.id() + ":" + accessType(player, claim);
            if (state.equals(BOSS_BAR_STATES.get(player.getUUID()))) continue;
            BOSS_BAR_STATES.put(player.getUUID(), state);

            ServerBossEvent bar = BOSS_BARS.computeIfAbsent(player.getUUID(), ignored -> {
                ServerBossEvent created = new ServerBossEvent(
                        UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS
                );
                created.setProgress(1);
                created.addPlayer(player);
                return created;
            });

            if (claim == null) {
                bar.setVisible(false);
                continue;
            }

            boolean owner = claim.ownerUuid().equals(player.getUUID());
            boolean allowed = claim.members().contains(player.getUUID()) || isOperator(player);
            bar.setName(Component.literal(owner
                    ? "Your claimed chunk"
                    : allowed ? claim.ownerName() + "'s claim - access granted" : "Claimed by " + claim.ownerName()));
            bar.setColor(owner ? BossEvent.BossBarColor.YELLOW : allowed ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED);
            bar.setVisible(true);
        }
    }

    private static boolean checkDirect(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) return true;
        boolean allowed = canAccess(serverPlayer, level, pos);
        if (!allowed) notifyDenied(serverPlayer);
        return allowed;
    }

    private static void notifyDenied(ServerPlayer player) {
        long now = System.currentTimeMillis();
        if (now - LAST_DENIED_MESSAGE.getOrDefault(player.getUUID(), 0L) < 750) return;
        LAST_DENIED_MESSAGE.put(player.getUUID(), now);
        player.sendOverlayMessage(Component.literal(ready ? "This chunk is protected." : "Claims are still loading."));
    }

    private static Claim claimAt(Level level, BlockPos pos) {
        return claims.get(new ClaimKey(level.dimension().identifier().toString(), ChunkPos.pack(pos)));
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && server.getPlayerList().isOp(player.nameAndId());
    }

    private static String accessType(ServerPlayer player, Claim claim) {
        if (claim.ownerUuid().equals(player.getUUID())) return "owner";
        return claim.members().contains(player.getUUID()) || isOperator(player) ? "member" : "blocked";
    }

    private static void removeBossBar(ServerPlayer player) {
        ServerBossEvent bar = BOSS_BARS.remove(player.getUUID());
        if (bar != null) bar.removePlayer(player);
        BOSS_BAR_STATES.remove(player.getUUID());
        LAST_DENIED_MESSAGE.remove(player.getUUID());
    }

    private record ClaimKey(String dimension, long chunk) {
    }

    private record Claim(String id, UUID ownerUuid, String ownerName, Set<UUID> members) {
    }
}
