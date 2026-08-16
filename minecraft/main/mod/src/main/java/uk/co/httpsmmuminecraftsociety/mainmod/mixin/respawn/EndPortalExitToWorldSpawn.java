package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndPortalBlock.class)
public class EndPortalExitToWorldSpawn {
    @Redirect(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnPositionAndUseSpawnBlock(ZLnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"
            )
    )
    private TeleportTransition useWorldSpawnForEndPortalExit(
            ServerPlayer player,
            boolean consumeSpawnBlockCharge,
            TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        ServerLevel world = player.level().getServer().findRespawnDimension();
        LevelData.RespawnData spawn = world.getRespawnData();

        return new TeleportTransition(
                world,
                Vec3.atBottomCenterOf(spawn.pos()),
                Vec3.ZERO,
                spawn.yaw(),
                spawn.pitch(),
                postTeleportTransition
        );
    }
}
