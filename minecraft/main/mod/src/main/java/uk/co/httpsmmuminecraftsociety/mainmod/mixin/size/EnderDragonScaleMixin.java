package uk.co.httpsmmuminecraftsociety.mainmod.mixin.size;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EnderDragon.class)
abstract class EnderDragonScaleMixin {
    @Inject(method = "sanitizeScale", at = @At("HEAD"), cancellable = true)
    private void mainmod$allowScale(float scale, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(scale);
    }

    @ModifyArgs(method = "tickPart", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;setPos(DDD)V"))
    private void mainmod$scalePartPosition(Args args) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        float scale = dragon.getScale();
        args.set(0, dragon.getX() + ((double) args.get(0) - dragon.getX()) * scale);
        args.set(1, dragon.getY() + ((double) args.get(1) - dragon.getY()) * scale);
        args.set(2, dragon.getZ() + ((double) args.get(2) - dragon.getZ()) * scale);
    }
}
