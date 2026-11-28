package uk.co.httpsmmuminecraftsociety.mainmod.mixin.size;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderDragonPart.class)
abstract class EnderDragonPartScaleMixin {
    @Shadow @Final public net.minecraft.world.entity.boss.enderdragon.EnderDragon parentMob;

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void mainmod$scalePart(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (parentMob != null) cir.setReturnValue(cir.getReturnValue().scale(parentMob.getScale()));
    }
}
