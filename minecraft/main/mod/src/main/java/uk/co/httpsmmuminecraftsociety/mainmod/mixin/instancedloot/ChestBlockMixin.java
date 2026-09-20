package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public abstract class ChestBlockMixin {
    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void pil$getInstancedMenuProvider(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
        MenuProvider provider = InstancedLootMenus.chestMenuProvider(state, level, pos);
        if (provider != null) {
            cir.setReturnValue(provider);
        }
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void pil$preventLootRegularPlacementConnection(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(InstancedLootMenus.preventPlacementConnection(context, cir.getReturnValue()));
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void pil$preventLootRegularNeighborConnection(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE && direction == ChestBlock.getConnectedDirection(state)) {
            BlockState updated = InstancedLootMenus.preventMismatchedConnection(level, pos, state, neighborPos);
            if (updated != state) {
                cir.setReturnValue(updated);
            }
        }
    }
}
