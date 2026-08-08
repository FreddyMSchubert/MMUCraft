package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(Armadillo.class)
public abstract class DailyArmadilloMixin {
    @Inject(method = "brushOffScute", at = @At("RETURN"))
    private void mainmod$recordBrush(Entity user, ItemStack brush, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && user instanceof ServerPlayer player) {
            DailyTaskManager.record(player, DailyTaskEvent.simple(DailySimpleEvent.BRUSH_ARMADILLO));
        }
    }
}
