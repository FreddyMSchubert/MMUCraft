package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceLitMixin {
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void mainmod$syncLitState(ServerLevel level, BlockPos pos, BlockState state,
                                              AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof AbstractFurnaceBlock)) return;
        boolean lit = ((FurnaceLitAccessor) furnace).mainmod$getLitTimeRemaining() > 0;
        if (current.getValue(AbstractFurnaceBlock.LIT) != lit) {
            level.setBlockAndUpdate(pos, current.setValue(AbstractFurnaceBlock.LIT, lit));
        }
    }
}
