package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(NoteBlock.class)
public abstract class DailyNoteBlockMixin {
    @Inject(method = "playNote", at = @At("HEAD"))
    private void mainmod$recordNoteInstrument(Entity source, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (source instanceof ServerPlayer player) {
            DailyTaskManager.record(player, DailyTaskEvent.of(
                    DailyTaskEvent.Type.PLAY_NOTE_BLOCK,
                    state.getValue(NoteBlock.INSTRUMENT).getSerializedName()
            ));
        }
    }
}
