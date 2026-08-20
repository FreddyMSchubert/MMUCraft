package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;

@Mixin(Entity.class)
public abstract class RemoveDecoBlockLights {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void mainmod$removeDecoBlockLight(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason.shouldDestroy() && (Object) this instanceof ItemFrame frame) {
            DecoBlocksManager.removeOwnedLight(frame);
        }
    }
}
