package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.bigboss.BigBossManager;


@Mixin(ServerPlayer.class)
public class RespawnNearDeath {
    private static final int MAX_SEARCHES = 64;
    private static final String VOID_DEATH_KEEP_INVENTORY_TAG = "mainmod_void_death_keep_inventory";

    @Shadow
    public MinecraftServer server;

    @Inject(method = "die", at = @At("HEAD"))
    private void markVoidDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        BigBossManager.killed(player, damageSource);

        if (damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void announceBossDeath(PlayerList playerList, Component message, boolean overlay) {
        if (!BigBossManager.announceDeath((ServerPlayer) (Object) this, message))
            playerList.broadcastSystemMessage(message, overlay);
    }

    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"), cancellable = true)
    private void useCustomRespawnPosition(
            boolean consumeSpawnBlockCharge,
            TeleportTransition.PostTeleportTransition postTeleportTransition,
            CallbackInfoReturnable<TeleportTransition> cir
    ) {
        if (!consumeSpawnBlockCharge) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        boolean missingRespawnBlock = cir.getReturnValue().missingRespawnBlock();
        if (player.getRespawnConfig() != null && !missingRespawnBlock) {
            return;
        }
        if (missingRespawnBlock) {
            player.setRespawnPosition(null, false);
        }

        if (!hasVoidDeathTag(player)) {
            ServerLevel level = player.level();
            BlockPos spawnPos = RespawnPositionFinder.find(player, level, player.blockPosition());
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
        if (oldPlayer.removeTag(BigBossManager.KEEP_TAG)) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            player.getInventory().replaceWith(oldPlayer.getInventory());
            player.experienceLevel = oldPlayer.experienceLevel;
            player.experienceProgress = oldPlayer.experienceProgress;
            player.totalExperience = oldPlayer.totalExperience;
        }
        if (!oldPlayer.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG)) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        player.getInventory().replaceWith(oldPlayer.getInventory());
    }

    private boolean hasVoidDeathTag(ServerPlayer player) {
        boolean removed = player.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        if (removed) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
        return removed;
    }

}
