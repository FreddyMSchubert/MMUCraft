package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(AbstractCow.class)
public abstract class DailyCowMixin {
    @Unique private boolean mainmod$usedBucket;

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mainmod$captureBucket(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        mainmod$usedBucket = player.getItemInHand(hand).is(Items.BUCKET);
    }

    @Inject(method = "mobInteract", at = @At("RETURN"))
    private void mainmod$recordMilking(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (mainmod$usedBucket && cir.getReturnValue().consumesAction() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.MILK_COW));
        }
    }
}
