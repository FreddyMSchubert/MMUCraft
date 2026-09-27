package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PotionOfDisplacementCharm implements Charm, ConsumableCallbacksCharm, TickCallbackCharm
{
    private static final int MAX_SEARCH_TICKS = 15 * 20;
    private static final int NORMAL_RADIUS = 7_500;
    private static final int MEMBER_RADIUS = 15_000;
    private static final String SEARCH_FAILED = "The Potion of Displacement could not find a safe destination. Try again.";
    private static final TicketType SEARCH_TICKET = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
    private static final Map<UUID, SearchSession> SEARCHES = new HashMap<>();
    private static final Map<PoolKey, DestinationPool> POOLS = new HashMap<>();

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session != null && (session.key.level != level
                || !session.key.center.equals(level.getRespawnData().globalPos().pos()))) {
            stopSearch(player.getUUID());
            session = null;
        }

        if (session == null) {
            String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
            if (!teleportPossibleTest.isEmpty()) {
                player.sendSystemMessage(Component.literal(teleportPossibleTest));
                player.stopUsingItem();
                return;
            }

            int radius = PlayerStatsSync.isMember(player) ? MEMBER_RADIUS : NORMAL_RADIUS;
            PoolKey key = new PoolKey(level, radius, level.getRespawnData().globalPos().pos());
            POOLS.computeIfAbsent(key, PotionOfDisplacementCharm::loadPool);
            session = new SearchSession(key);
            SEARCHES.put(player.getUUID(), session);
        }
    }

    @Override
    public boolean shouldFinishConsumptionEarly(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            int elapsedTicks,
            int charmLevel
    ) {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session == null) return false;

        return hasReadyDestination(session.key) || searchTicks(session) >= MAX_SEARCH_TICKS;
    }

    @Override
    public boolean onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal(SEARCH_FAILED));
            stopSearch(player.getUUID());
            return false;
        }

        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            stopSearch(player.getUUID());
            return false;
        }

        PoolEntry entry = takeReadyDestination(session.key);
        if (entry == null) {
            player.sendSystemMessage(Component.literal(SEARCH_FAILED));
            stopSearch(player.getUUID());
            return false;
        }

        BlockPos destination = entry.destination;
        boolean teleported;
        try {
            teleported = TeleportPotionUtils.teleportWithCompanions(
                    "displacement",
                    player,
                    level,
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
            );
        } finally {
            DestinationPool pool = POOLS.get(session.key);
            if (pool != null) {
                pool.entries.remove(entry);
                persistPool(session.key, pool);
            }
            releaseEntry(session.key, entry);
        }

        if (!teleported) {
            player.sendSystemMessage(Component.literal("The Potion of Displacement could not teleport you. Try again."));
            stopSearch(player.getUUID());
            return false;
        }

        player.fallDistance = 0.0F;
        stack.consume(1, player);
        stopSearch(player.getUUID());
        return true;
    }

    @Override
    public void onTick(ServerPlayer player, ServerLevel level) {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session == null) return;

        boolean stillDrinkingDisplacementPotion = player.isUsingItem()
                && CharmsManager.hasAbility(player.getUseItem(), PotionOfDisplacementCharm.class);
        if (session.key.level != level || !stillDrinkingDisplacementPotion) {
            stopSearch(player.getUUID());
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        stopSearch(player.getUUID());
    }

    public static void clearSearches() {
        SEARCHES.clear();
        for (Map.Entry<PoolKey, DestinationPool> pool : POOLS.entrySet()) {
            for (PoolEntry entry : pool.getValue().entries) {
                releaseEntry(pool.getKey(), entry);
            }
        }
        POOLS.clear();
    }

    public static void tickPools(ServerLevel level) {
        BlockPos center = level.getRespawnData().globalPos().pos();
        POOLS.computeIfAbsent(new PoolKey(level, NORMAL_RADIUS, center), PotionOfDisplacementCharm::loadPool);
        POOLS.computeIfAbsent(new PoolKey(level, MEMBER_RADIUS, center), PotionOfDisplacementCharm::loadPool);
        Iterator<Map.Entry<PoolKey, DestinationPool>> pools = POOLS.entrySet().iterator();
        while (pools.hasNext()) {
            Map.Entry<PoolKey, DestinationPool> pool = pools.next();
            PoolKey key = pool.getKey();
            if (key.level != level) continue;
            if (!key.center.equals(center)) {
                for (PoolEntry entry : pool.getValue().entries) releaseEntry(key, entry);
                pools.remove();
                continue;
            }
            advancePool(key, pool.getValue());
        }
    }

    private static void advancePool(PoolKey key, DestinationPool pool) {
        boolean changed = false;
        Iterator<PoolEntry> entries = pool.entries.iterator();
        while (entries.hasNext()) {
            PoolEntry entry = entries.next();
            if (entry.destination != null || entry.inUse || !entry.loading.isDone()) continue;

            if (!entry.loading.isCompletedExceptionally()) {
                Object result = entry.loading.getNow(null);
                if (!(result instanceof ChunkResult<?> chunkResult) || chunkResult.isSuccess()) {
                    LevelChunk chunk = key.level.getChunkSource()
                            .getChunkNow(entry.chunkPos.x(), entry.chunkPos.z());
                    if (chunk == null) continue;
                    entry.destination = possibleSpawnPos(entry.x, entry.z, key.level, chunk);
                }
            }
            if (entry.destination != null) {
                pool.failures = 0;
                pool.retryAt = 0;
            } else {
                releaseEntry(key, entry);
                entries.remove();
                changed = true;
                failedCandidate(key, pool);
            }
        }

        if (key.level.getGameTime() < pool.retryAt) {
            if (changed) persistPool(key, pool);
            return;
        }
        while (pool.entries.size() < 3) {
            PoolEntry entry = requestCandidate(key);
            if (entry == null) {
                failedCandidate(key, pool);
                break;
            }
            pool.entries.add(entry);
            changed = true;
        }
        if (changed) persistPool(key, pool);
    }

    private static void failedCandidate(PoolKey key, DestinationPool pool) {
        if (++pool.failures >= 16) {
            pool.failures = 0;
            pool.retryAt = key.level.getGameTime() + 20 * 60;
        }
    }

    private static boolean hasReadyDestination(PoolKey key) {
        return readyDestination(key, false) != null;
    }

    private static PoolEntry takeReadyDestination(PoolKey key) {
        return readyDestination(key, true);
    }

    private static PoolEntry readyDestination(PoolKey key, boolean reserve) {
        DestinationPool pool = POOLS.get(key);
        if (pool == null) return null;
        Iterator<PoolEntry> entries = pool.entries.iterator();
        while (entries.hasNext()) {
            PoolEntry entry = entries.next();
            if (entry.destination == null || entry.inUse) continue;
            LevelChunk chunk = key.level.getChunkSource().getChunkNow(entry.chunkPos.x(), entry.chunkPos.z());
            BlockPos destination = chunk == null ? null : possibleSpawnPos(entry.x, entry.z, key.level, chunk);
            if (destination == null || !key.level.getWorldBorder().isWithinBounds(destination)) {
                entries.remove();
                releaseEntry(key, entry);
                persistPool(key, pool);
                failedCandidate(key, pool);
                continue;
            }
            entry.destination = destination;
            if (reserve) entry.inUse = true;
            return entry;
        }
        return null;
    }

    private static PoolEntry requestCandidate(PoolKey key) {
        BlockPos candidatePos = randomCandidatePosition(key.level, key.center, key.radius);
        if (candidatePos == null) return null;
        return requestAt(key, candidatePos.getX(), candidatePos.getZ());
    }

    private static PoolEntry requestAt(PoolKey key, int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        for (Map.Entry<PoolKey, DestinationPool> pool : POOLS.entrySet()) {
            if (pool.getKey().level != key.level) continue;
            if (pool.getValue().entries.stream().anyMatch(entry -> entry.chunkPos.equals(chunkPos))) return null;
        }
        try {
            CompletableFuture<?> loading = key.level.getChunkSource()
                    .addTicketAndLoadWithRadius(SEARCH_TICKET, chunkPos, 0);
            return new PoolEntry(x, z, chunkPos, loading);
        } catch (RuntimeException ignored) {
            key.level.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, chunkPos, 0);
            return null;
        }
    }

    private static DestinationPool loadPool(PoolKey key) {
        DestinationPool pool = new DestinationPool();
        CompoundTag data = key.level.getServer().getCommandStorage().get(storageId(key));
        if (data.getInt("centerX").orElse(Integer.MIN_VALUE) != key.center.getX()
                || data.getInt("centerZ").orElse(Integer.MIN_VALUE) != key.center.getZ()) return pool;

        ListTag saved = data.getListOrEmpty("candidates");
        boolean changed = false;
        for (int index = 0; index < saved.size() && pool.entries.size() < 3; index++) {
            CompoundTag candidate = saved.getCompoundOrEmpty(index);
            Integer x = candidate.getInt("x").orElse(null);
            Integer z = candidate.getInt("z").orElse(null);
            if (x == null || z == null) {
                changed = true;
                continue;
            }
            long dx = (long) x - key.center.getX();
            long dz = (long) z - key.center.getZ();
            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            if (dx * dx + dz * dz > (long) key.radius * key.radius
                    || !key.level.getWorldBorder().isWithinBounds(x + 0.5D, z + 0.5D)
                    || pool.entries.stream().anyMatch(entry -> entry.chunkPos.equals(chunkPos))) {
                changed = true;
                continue;
            }
            PoolEntry entry = requestAt(key, x, z);
            if (entry == null) {
                changed = true;
                continue;
            }
            pool.entries.add(entry);
        }
        if (changed || saved.size() > pool.entries.size()) persistPool(key, pool);
        return pool;
    }

    private static void persistPool(PoolKey key, DestinationPool pool) {
        CompoundTag data = new CompoundTag();
        data.putInt("centerX", key.center.getX());
        data.putInt("centerZ", key.center.getZ());
        ListTag candidates = new ListTag();
        for (PoolEntry entry : pool.entries) {
            CompoundTag candidate = new CompoundTag();
            candidate.putInt("x", entry.x);
            candidate.putInt("z", entry.z);
            candidates.add(candidate);
        }
        data.put("candidates", candidates);
        key.level.getServer().getCommandStorage().set(storageId(key), data);
    }

    private static Identifier storageId(PoolKey key) {
        Identifier dimension = key.level.dimension().identifier();
        return Identifier.fromNamespaceAndPath("mainmod",
                "displacement_pool/" + dimension.getNamespace() + "/" + dimension.getPath() + "/" + key.radius);
    }

    private static BlockPos randomCandidatePosition(ServerLevel level, BlockPos origin, int radius) {
        WorldBorder border = level.getWorldBorder();
        int minX = Math.max(Mth.ceil(border.getMinX()), radiusMin(origin.getX(), radius));
        int maxX = Math.min(Mth.floor(border.getMaxX() - 1.0D), radiusMax(origin.getX(), radius));
        int minZ = Math.max(Mth.ceil(border.getMinZ()), radiusMin(origin.getZ(), radius));
        int maxZ = Math.min(Mth.floor(border.getMaxZ() - 1.0D), radiusMax(origin.getZ(), radius));

        if (minX > maxX || minZ > maxZ) return null;

        var chunks = level.getChunkSource();
        var generator = chunks.getGenerator();
        var biomes = generator.getBiomeSource().createUncachedResolver(chunks.randomState());
        int biomeY = generator.getSeaLevel() >> 2;
        for (int attempt = 0; attempt < 32; attempt++) {
            int x = Mth.nextInt(level.getRandom(), minX, maxX);
            int z = Mth.nextInt(level.getRandom(), minZ, maxZ);
            long dx = (long) x - origin.getX();
            long dz = (long) z - origin.getZ();
            if (dx * dx + dz * dz > (long) radius * radius) continue;
            var biome = biomes.getNoiseBiome(x >> 2, biomeY, z >> 2);
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) continue;
            return new BlockPos(x, origin.getY(), z);
        }

        return null;
    }

    private static int radiusMin(int origin, int radius) {
        return (int) Math.max(Integer.MIN_VALUE, (long) origin - radius);
    }

    private static int radiusMax(int origin, int radius) {
        return (int) Math.min(Integer.MAX_VALUE, (long) origin + radius);
    }

    private static BlockPos possibleSpawnPos(int x, int z, ServerLevel level, LevelChunk chunk) {
        int minY = level.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, level.getMaxY(), z);

        if (level.dimension().equals(Level.NETHER)) {
            cursor.setY(126);
        }

        while (cursor.getY() > minY && !chunk.getBlockState(cursor).isAir()) {
            cursor.move(0, -1, 0);
        }

        while (cursor.getY() > minY && chunk.getBlockState(cursor).isAir()) {
            cursor.move(0, -1, 0);
        }

        if (cursor.getY() <= minY) return null;

        BlockState groundState = chunk.getBlockState(cursor);
        if (groundState.getFluidState().is(FluidTags.LAVA)
                || groundState.getFluidState().is(FluidTags.WATER)) {
            return null;
        }

        BlockPos spawnPos = cursor.above();
        BlockPos headPos = spawnPos.above();
        if (!chunk.getBlockState(spawnPos).isAir() || !chunk.getBlockState(headPos).isAir()) {
            return null;
        }

        return spawnPos.immutable();
    }

    private static void stopSearch(UUID playerId) {
        SEARCHES.remove(playerId);
    }

    private static void releaseEntry(PoolKey key, PoolEntry entry) {
        key.level.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, entry.chunkPos, 0);
    }

    private static int searchTicks(SearchSession session) {
        long ticks = (System.nanoTime() - session.startedAtNanos) / 50_000_000L + 1L;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, ticks));
    }

    private record PoolKey(ServerLevel level, int radius, BlockPos center) {}

    private static final class DestinationPool {
        private final List<PoolEntry> entries = new ArrayList<>();
        private int failures;
        private long retryAt;
    }

    private static final class PoolEntry {
        private final int x;
        private final int z;
        private final ChunkPos chunkPos;
        private final CompletableFuture<?> loading;
        private BlockPos destination;
        private boolean inUse;

        private PoolEntry(int x, int z, ChunkPos chunkPos, CompletableFuture<?> loading) {
            this.x = x;
            this.z = z;
            this.chunkPos = chunkPos;
            this.loading = loading;
        }
    }

    private static final class SearchSession {
        private final PoolKey key;
        private final long startedAtNanos;

        private SearchSession(PoolKey key) {
            this.key = key;
            this.startedAtNanos = System.nanoTime();
        }
    }
}
