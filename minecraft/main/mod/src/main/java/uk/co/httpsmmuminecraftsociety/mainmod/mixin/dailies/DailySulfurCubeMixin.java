package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(SulfurCube.class)
public abstract class DailySulfurCubeMixin {
    @Unique
    private Vec3 mainmod$velocityBeforePush;

    @Inject(method = "playerPush", at = @At("HEAD"))
    private void mainmod$captureVelocity(Player player, CallbackInfo ci) {
        this.mainmod$velocityBeforePush = ((Entity)(Object)this).getDeltaMovement();
    }

    @Inject(method = "playerPush", at = @At("RETURN"))
    private void mainmod$recordKick(Player player, CallbackInfo ci) {
        Vec3 after = ((Entity)(Object)this).getDeltaMovement();
        if (player instanceof ServerPlayer serverPlayer && this.mainmod$velocityBeforePush != null
                && !after.equals(this.mainmod$velocityBeforePush)) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.KICK_SULFUR_CUBE));
        }
    }
}
