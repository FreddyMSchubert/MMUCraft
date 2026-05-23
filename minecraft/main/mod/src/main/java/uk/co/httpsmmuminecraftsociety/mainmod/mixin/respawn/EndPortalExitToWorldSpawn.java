package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
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
        return TeleportTransition.createDefault(player, postTeleportTransition);
    }
}
