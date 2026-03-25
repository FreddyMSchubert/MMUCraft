package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ServerPlayer.class)
public class RespawnNearDeath {
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 256;
    private static final int MAX_SEARCHES = 16;

    @Inject(method = "die", at = @At("HEAD"))
    private void updateRespawnPoint(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
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

        BlockPos found = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(x, z));
        if (found == null) {
            return null;
        }

        return level.getWorldBorder().isWithinBounds(found) ? found : null;
    }

    private int offsetCoordinate(int origin) {
        int distance = ThreadLocalRandom.current().nextInt(MIN_DISTANCE, MAX_DISTANCE);
        int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        return origin + (distance * direction);
    }
}
