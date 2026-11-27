package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.Presents;

@Mixin(BlockAttachedEntity.class)
public abstract class PresentFrameTick {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$tickPresent(CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame frame) Presents.tick(frame);
    }
}
