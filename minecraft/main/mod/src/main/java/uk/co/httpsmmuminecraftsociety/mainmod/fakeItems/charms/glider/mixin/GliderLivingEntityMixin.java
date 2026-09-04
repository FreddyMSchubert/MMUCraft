package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderFlight;

@Mixin(LivingEntity.class)
public abstract class GliderLivingEntityMixin {
    @Inject(method = "canGlide", at = @At("HEAD"), cancellable = true)
    private void mainmod$stopGliderInFluids(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (GliderCharm.isGlider(entity.getItemBySlot(EquipmentSlot.CHEST)) && GliderFlight.touchesFluid(entity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "applyPostImpulseGraceTime", at = @At("TAIL"))
    private void mainmod$allowGliderImpulseAscent(int ticks, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && ticks > 0) {
            GliderFlight.allowImpulseAscent(player);
        }
    }
}
