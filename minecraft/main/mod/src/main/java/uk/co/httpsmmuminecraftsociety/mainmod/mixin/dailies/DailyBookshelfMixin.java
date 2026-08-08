package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(ChiseledBookShelfBlock.class)
public abstract class DailyBookshelfMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void mainmod$recordStoredBook(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && stack.is(ItemTags.BOOKSHELF_BOOKS)
                && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.FILL_BOOKSHELF));
        }
    }
}
