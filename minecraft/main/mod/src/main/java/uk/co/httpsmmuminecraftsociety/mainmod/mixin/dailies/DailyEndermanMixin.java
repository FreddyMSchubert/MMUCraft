package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(EnderMan.class)
public abstract class DailyEndermanMixin {
    @Inject(method = "isBeingStaredBy", at = @At("RETURN"))
    private void mainmod$recordEyeContact(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.EYE_CONTACT_ENDERMAN));
        }
    }
}
