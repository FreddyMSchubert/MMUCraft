package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;

@Mixin(BlockItem.class)
public abstract class PlaceFakeItemsAsFrames {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void mainmod$placeFakeItemAsFrame(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        InteractionResult result = DecoBlocksManager.onPlaceBlock(context);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
