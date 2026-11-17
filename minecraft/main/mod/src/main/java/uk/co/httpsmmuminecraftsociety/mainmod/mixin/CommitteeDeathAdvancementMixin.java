package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;

@Mixin(ServerPlayer.class)
public abstract class CommitteeDeathAdvancementMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void mainmod$checkCommitteeDrop(DamageSource source, CallbackInfo callback) {
        MasteryAdvancements.recordCommitteeDeath((ServerPlayer) (Object) this);
    }
}
