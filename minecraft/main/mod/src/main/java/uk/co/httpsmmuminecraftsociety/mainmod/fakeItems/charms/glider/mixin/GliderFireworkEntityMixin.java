package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;

@Mixin(FireworkRocketEntity.class)
public abstract class GliderFireworkEntityMixin {
    @Shadow private LivingEntity attachedToEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mainmod$stopRocketAfterGliderSwap(CallbackInfo ci) {
        if (attachedToEntity != null && GliderCharm.isGlider(attachedToEntity.getItemBySlot(EquipmentSlot.CHEST))) {
            ((FireworkRocketEntity) (Object) this).discard();
            ci.cancel();
        }
    }
}
