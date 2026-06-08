package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;

@Mixin(ItemFrame.class)
public class RemoveEmptyDecoBlockFrames {
    @Inject(
            method = "setItem(Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("TAIL")
    )
    private void mainmod$removeDecoBlockFrameWhenEmptied(ItemStack stack, boolean updateNeighbour, CallbackInfo ci) {
        ItemFrame frame = (ItemFrame) (Object) this;
        if (frame.level().isClientSide() || frame.isRemoved()) {
            return;
        }

        if (frame.entityTags().contains(DecoBlocksManager.DECO_BLOCK_FRAME_TAG) && stack.isEmpty()) {
            frame.discard();
        }
    }
}
