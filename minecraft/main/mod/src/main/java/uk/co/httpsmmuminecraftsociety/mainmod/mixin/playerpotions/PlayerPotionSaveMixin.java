package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;

@Mixin(ServerPlayer.class)
abstract class PlayerPotionSaveMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mainmod$readDisguise(ValueInput input, CallbackInfo ci) {
        PlayerDisguises.read((ServerPlayer) (Object) this, input);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mainmod$writeDisguise(ValueOutput output, CallbackInfo ci) {
        PlayerDisguises.write((ServerPlayer) (Object) this, output);
    }
}
