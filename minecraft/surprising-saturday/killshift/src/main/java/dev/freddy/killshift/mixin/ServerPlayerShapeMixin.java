package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerShapeMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void killshift$readForm(ValueInput input, CallbackInfo callback) {
        ShapeManager.readSaved((ServerPlayer) (Object) this, input);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void killshift$saveForm(ValueOutput output, CallbackInfo callback) {
        ShapeManager.writeSaved((ServerPlayer) (Object) this, output);
    }

    @Inject(method = "setLastClientInput", at = @At("TAIL"))
    private void killshift$playSneakSound(Input input, CallbackInfo callback) {
        ShapeManager.onSneakInput((ServerPlayer) (Object) this, input.shift());
    }
}
