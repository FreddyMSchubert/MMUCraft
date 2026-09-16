package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PotionOfDisplacementCharm implements Charm, ConsumableCallbacksCharm, TickCallbackCharm
{
    private static final int MIN_SEARCH_TICKS = 5 * 20;
    private static final int MAX_SEARCH_TICKS = 20 * 20;
    private static final TicketType SEARCH_TICKET = new TicketType(MAX_SEARCH_TICKS + 20L, TicketType.FLAG_LOADING);
    private static final Map<UUID, SearchSession> SEARCHES = new HashMap<>();

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session != null && (session.level != level || elapsedTicks <= session.lastElapsedTicks)) {
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

            int radius = PlayerStatsSync.isMember(player) ? 15_000 : 7_500;
            session = new SearchSession(level, player.blockPosition(), radius);
            SEARCHES.put(player.getUUID(), session);
        }

        session.lastElapsedTicks = elapsedTicks;
        advanceSearch(session);
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
        return elapsedTicks >= MIN_SEARCH_TICKS && session != null && session.destination != null;
    }

    @Override
    public boolean onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        SearchSession session = SEARCHES.get(player.getUUID());
        if (session != null && session.destination == null) {
            advanceSearch(session);
        }

        if (session == null || session.destination == null) {
            player.sendSystemMessage(Component.literal(
                    "The Potion of Displacement could not find a safe, dry destination within 20 seconds."
            ));
            stopSearch(player.getUUID());
            return false;
        }

        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            stopSearch(player.getUUID());
            return false;
        }

        BlockPos destination = session.destination;
        TeleportPotionUtils.teleportWithCompanions(
                "displacement",
                player,
                level,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );

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
        if (session.level != level || !stillDrinkingDisplacementPotion) {
            stopSearch(player.getUUID());
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        stopSearch(player.getUUID());
    }

    public static void clearSearches() {
        for (UUID playerId : SEARCHES.keySet().toArray(UUID[]::new)) {
            stopSearch(playerId);
        }
    }

    private static void advanceSearch(SearchSession session) {
        if (session.destination != null) return;

        if (session.candidate == null) {
            session.candidate = requestCandidate(session);
            return;
        }

        Candidate candidate = session.candidate;
        if (!candidate.loading().isDone()) return;

        LevelChunk chunk = candidate.loading().isCompletedExceptionally()
                ? null
                : session.level.getChunkSource().getChunkNow(candidate.chunkPos().x(), candidate.chunkPos().z());
        if (chunk != null) {
            session.destination = possibleSpawnPos(candidate.x(), candidate.z(), session.level, chunk);
        }

        if (session.destination == null) {
            releaseCandidate(session);
        }
    }

    private static Candidate requestCandidate(SearchSession session) {
        BlockPos candidatePos = randomCandidatePosition(session.level, session.origin, session.radius);
        if (candidatePos == null) return null;

        ChunkPos chunkPos = new ChunkPos(candidatePos.getX() >> 4, candidatePos.getZ() >> 4);
        try {
            CompletableFuture<?> loading = session.level.getChunkSource()
                    .addTicketAndLoadWithRadius(SEARCH_TICKET, chunkPos, 0);
            return new Candidate(candidatePos.getX(), candidatePos.getZ(), chunkPos, loading);
        } catch (RuntimeException exception) {
            session.level.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, chunkPos, 0);
            MainMod.LOGGER.warn("Could not schedule a displacement-potion destination chunk", exception);
            return null;
        }
    }

    private static BlockPos randomCandidatePosition(ServerLevel level, BlockPos origin, int radius) {
        WorldBorder border = level.getWorldBorder();
        int minX = Math.max(Mth.ceil(border.getMinX()), radiusMin(origin.getX(), radius));
        int maxX = Math.min(Mth.floor(border.getMaxX() - 1.0D), radiusMax(origin.getX(), radius));
        int minZ = Math.max(Mth.ceil(border.getMinZ()), radiusMin(origin.getZ(), radius));
        int maxZ = Math.min(Mth.floor(border.getMaxZ() - 1.0D), radiusMax(origin.getZ(), radius));

        if (minX > maxX || minZ > maxZ) return null;

        for (int attempt = 0; attempt < 32; attempt++) {
            int x = Mth.nextInt(level.getRandom(), minX, maxX);
            int z = Mth.nextInt(level.getRandom(), minZ, maxZ);
            long dx = (long) x - origin.getX();
            long dz = (long) z - origin.getZ();
            if (dx * dx + dz * dz <= (long) radius * radius) {
                return new BlockPos(x, origin.getY(), z);
            }
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
        SearchSession session = SEARCHES.remove(playerId);
        if (session != null) releaseCandidate(session);
    }

    private static void releaseCandidate(SearchSession session) {
        if (session.candidate == null) return;
        session.level.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, session.candidate.chunkPos(), 0);
        session.candidate = null;
    }

    private record Candidate(int x, int z, ChunkPos chunkPos, CompletableFuture<?> loading) {}

    private static final class SearchSession {
        private final ServerLevel level;
        private final BlockPos origin;
        private final int radius;
        private int lastElapsedTicks = -1;
        private Candidate candidate;
        private BlockPos destination;

        private SearchSession(ServerLevel level, BlockPos origin, int radius) {
            this.level = level;
            this.origin = origin;
            this.radius = radius;
        }
    }
}
