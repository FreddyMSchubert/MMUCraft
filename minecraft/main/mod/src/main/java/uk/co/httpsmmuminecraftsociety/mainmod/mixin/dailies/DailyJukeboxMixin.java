package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(JukeboxPlayable.class)
public abstract class DailyJukeboxMixin {
    @Inject(method = "tryInsertIntoJukebox", at = @At("RETURN"))
    private static void mainmod$recordDailyMusicDisc(
            Level level,
            BlockPos pos,
            ItemStack stack,
            Player player,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (player instanceof ServerPlayer serverPlayer && callback.getReturnValue().consumesAction()) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.PLAY_MUSIC_DISC));
        }
    }
}
