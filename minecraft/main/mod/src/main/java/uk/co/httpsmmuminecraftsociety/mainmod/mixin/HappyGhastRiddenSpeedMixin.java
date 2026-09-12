package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.HappyGhastSpeedCharm;

@Mixin(HappyGhast.class)
public final class HappyGhastRiddenSpeedMixin
{
    @Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
    private void mainmod$applyRiderCharmSpeed(
            Player rider,
            Vec3 travelVector,
            CallbackInfoReturnable<Vec3> callback
    ) {
        if (!(rider instanceof ServerPlayer serverRider)) return;

        int charmLevel = CharmsManager.getPlayerCharmLevel(serverRider, HappyGhastSpeedCharm.class);
        callback.setReturnValue(HappyGhastSpeedCharm.adjustRiddenInput(callback.getReturnValue(), charmLevel));
    }
}
