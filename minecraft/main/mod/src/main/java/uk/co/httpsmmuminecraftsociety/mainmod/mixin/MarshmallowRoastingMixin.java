package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.magic.MarshmallowRoasting;

@Mixin(ServerPlayer.class)
public abstract class MarshmallowRoastingMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$roastMarshmallows(CallbackInfo ci) {
        MarshmallowRoasting.tick((ServerPlayer) (Object) this);
    }
}
