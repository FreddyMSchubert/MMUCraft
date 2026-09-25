package dev.freddy.killshift.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
abstract class LocatorPlayerMixin {
    @Redirect(method = "makeWaypointConnectionWith", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;doesSourceIgnoreReceiver(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"))
    private boolean killshift$showEveryPlayer(LivingEntity source, ServerPlayer receiver) {
        if (source instanceof ServerPlayer player) return player.level() != receiver.level();
        return WaypointTransmitter.doesSourceIgnoreReceiver(source, receiver);
    }
}
