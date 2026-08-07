package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(CakeBlock.class)
public abstract class DailyCakeMixin {
    @Inject(method = "eat", at = @At("RETURN"))
    private static void mainmod$recordCakeSlice(LevelAccessor level, BlockPos pos, BlockState state, Player player,
                                                CallbackInfoReturnable<InteractionResult> callback) {
        if (callback.getReturnValue().consumesAction() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, new DailyTaskEvent(
                    DailyTaskEvent.Type.USE_ITEM,
                    DailyTargetId.of(Items.CAKE),
                    "eat",
                    1
            ));
        }
    }
}
