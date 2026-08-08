package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(BellBlock.class)
public abstract class DailyBellMixin {
    @Inject(method = "onHit", at = @At("RETURN"))
    private void mainmod$recordBellRing(Level level, BlockState state, BlockHitResult hit, Player player, boolean checkHit,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.RING_BELL));
        }
    }
}
