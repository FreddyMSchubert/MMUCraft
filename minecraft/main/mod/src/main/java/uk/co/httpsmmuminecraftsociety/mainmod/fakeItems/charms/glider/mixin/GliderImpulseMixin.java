package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderFlight;

@Mixin(Entity.class)
public abstract class GliderImpulseMixin {
    @Inject(method = "push(DDD)V", at = @At("TAIL"))
    private void mainmod$allowUpwardImpulse(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && y > 0
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            GliderFlight.allowImpulseAscent(player);
        }
    }
}
