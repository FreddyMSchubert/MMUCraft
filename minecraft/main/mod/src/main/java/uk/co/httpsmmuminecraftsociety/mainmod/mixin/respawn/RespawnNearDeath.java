package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ServerPlayer.class)
public class RespawnNearDeath {
    private static final int MAX_SEARCHES = 64;
    private static final int DRY_SEARCHES = 8;
    private static final int RING_SEARCHES = 12;
    private static final String VOID_DEATH_KEEP_INVENTORY_TAG = "mainmod_void_death_keep_inventory";

    @Shadow
    public MinecraftServer server;

    @Inject(method = "die", at = @At("HEAD"))
    private void markVoidDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        if (damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
    }

    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void useCustomRespawnPosition(
            boolean consumeSpawnBlockCharge,
            TeleportTransition.PostTeleportTransition postTeleportTransition,
            CallbackInfoReturnable<TeleportTransition> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!hasVoidDeathTag(player)) {
            if (!consumeSpawnBlockCharge || player.getRespawnConfig() != null) {
                return;
            }

            ServerLevel level = player.level();
            BlockPos spawnPos = findRespawnPosition(player, level);
            if (spawnPos == null) {
                MainMod.LOGGER.info("Could not locate a respawn position within {} tries; using world spawn", MAX_SEARCHES);
                return;
            }

            cir.setReturnValue(new TeleportTransition(
                    level,
                    new Vec3(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5),
                    Vec3.ZERO,
                    0.0F,
                    0.0F,
                    postTeleportTransition
            ));
            return;
        }

        ServerLevel overworld = this.server.overworld();
        LevelData.RespawnData respawnData = overworld.getRespawnData();
        Vec3 spawnPos = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, respawnData.pos()));

        cir.setReturnValue(new TeleportTransition(
                overworld,
                spawnPos,
                Vec3.ZERO,
                respawnData.yaw(),
                respawnData.pitch(),
                postTeleportTransition
        ));
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void restoreInventoryAfterVoidDeath(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        if (!oldPlayer.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG)) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        player.getInventory().replaceWith(oldPlayer.getInventory());
    }

    private BlockPos findRespawnPosition(ServerPlayer player, ServerLevel level) {
        for (int attempt = 0; attempt < MAX_SEARCHES; attempt++) {
            BlockPos candidate = locateCandidate(player, level, attempt < DRY_SEARCHES, attempt < RING_SEARCHES);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos locateCandidate(ServerPlayer player, ServerLevel level, boolean requireDryLand, boolean usePreferredDistance) {
        BlockPos deathPos = player.blockPosition();
        double maxDistance = server.getPlayerList().getViewDistance() * (PlayerStatsSync.isMember(player) ? 8.0 : 16.0);
        double minDistance = usePreferredDistance ? maxDistance * 0.5 : 0.0;
        double angle = ThreadLocalRandom.current().nextDouble(Math.TAU);
        double distance = ThreadLocalRandom.current().nextDouble(minDistance, maxDistance);
        int x = deathPos.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = deathPos.getZ() + (int) Math.round(Math.sin(angle) * distance);

        BlockPos found = requireDryLand || level.dimensionType().hasCeiling()
                ? PlayerSpawnFinder.getSpawnPosInChunk(
                        level,
                        new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
                )
                : new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (found == null || found.getY() <= level.getMinY() || found.getY() + 1 >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(found)
                || !level.getBlockState(found).getBlock().isPossibleToRespawnInThis(level.getBlockState(found))
                || !level.getBlockState(found.above()).getBlock().isPossibleToRespawnInThis(level.getBlockState(found.above()))) {
            return null;
        }

        BlockPos floor = found.below();
        if (level.getFluidState(floor).is(FluidTags.LAVA)
                || (!level.getFluidState(floor).is(FluidTags.WATER)
                && !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP))) {
            return null;
        }

        long dx = found.getX() - deathPos.getX();
        long dz = found.getZ() - deathPos.getZ();
        double distanceSquared = dx * dx + dz * dz;
        return distanceSquared >= minDistance * minDistance && distanceSquared <= maxDistance * maxDistance
                ? found
                : null;
    }

    private boolean hasVoidDeathTag(ServerPlayer player) {
        boolean removed = player.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        if (removed) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
        return removed;
    }

}
