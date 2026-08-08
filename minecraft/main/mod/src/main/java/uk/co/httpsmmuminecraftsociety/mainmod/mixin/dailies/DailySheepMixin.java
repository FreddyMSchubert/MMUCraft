package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(Sheep.class)
public abstract class DailySheepMixin {
    @Unique private boolean mainmod$wasSheared;

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mainmod$captureSheared(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        mainmod$wasSheared = ((Sheep)(Object)this).isSheared();
    }

    @Inject(method = "mobInteract", at = @At("RETURN"))
    private void mainmod$recordShearing(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!mainmod$wasSheared && ((Sheep)(Object)this).isSheared() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.SHEAR_SHEEP));
        }
    }
}
