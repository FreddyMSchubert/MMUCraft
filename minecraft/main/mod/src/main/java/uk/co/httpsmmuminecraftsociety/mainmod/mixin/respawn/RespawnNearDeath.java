package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.ChunkPos;
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

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ServerPlayer.class)
public class RespawnNearDeath {
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 256;
    private static final int MAX_SEARCHES = 16;
    private static final String VOID_DEATH_KEEP_INVENTORY_TAG = "mainmod_void_death_keep_inventory";

    @Shadow
    public MinecraftServer server;

    @Inject(method = "die", at = @At("HEAD"))
    private void updateRespawnPoint(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        if (damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
            player.setRespawnPosition(null, false);
            return;
        }

        ServerLevel level = player.level();
        BlockPos spawnPos = findRespawnPosition(player, level);

        if (spawnPos == null) {
            MainMod.LOGGER.info("Could not locate a respawn position within {} tries", MAX_SEARCHES);
            return;
        }

        LevelData.RespawnData respawnData = new LevelData.RespawnData(
                new GlobalPos(level.dimension(), spawnPos),
                0.0F,
                0.0F
        );

        ServerPlayer.RespawnConfig config = new ServerPlayer.RespawnConfig(respawnData, true);
        player.setRespawnPosition(config, false);
    }

    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void useOverworldSpawnAfterVoidDeath(
            boolean consumeSpawnBlockCharge,
            TeleportTransition.PostTeleportTransition postTeleportTransition,
            CallbackInfoReturnable<TeleportTransition> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!hasVoidDeathTag(player)) {
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
            BlockPos candidate = locateCandidate(player, level);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos locateCandidate(ServerPlayer player, ServerLevel level) {
        BlockPos deathPos = player.blockPosition();
        int x = offsetCoordinate(deathPos.getX());
        int z = offsetCoordinate(deathPos.getZ());

        BlockPos found = PlayerSpawnFinder.getSpawnPosInChunk(
                level,
                new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
        );
        if (found == null) {
            return null;
        }

        return level.getWorldBorder().isWithinBounds(found) ? found : null;
    }

    private boolean hasVoidDeathTag(ServerPlayer player) {
        boolean removed = player.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        if (removed) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
        return removed;
    }

    private int offsetCoordinate(int origin) {
        int distance = ThreadLocalRandom.current().nextInt(MIN_DISTANCE, MAX_DISTANCE);
        int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        return origin + (distance * direction);
    }
}
