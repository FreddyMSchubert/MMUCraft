package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(HangingEntityItem.class)
public abstract class DailyHangingEntityMixin {
    @Inject(method = "useOn", at = @At("RETURN"))
    private void mainmod$recordPainting(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()
                && context.getItemInHand().is(Items.PAINTING)
                && context.getPlayer() instanceof ServerPlayer player) {
            DailyTaskManager.record(player, DailyTaskEvent.simple(DailySimpleEvent.HANG_PAINTING));
        }
    }
}
