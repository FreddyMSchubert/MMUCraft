package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(Player.class)
abstract class PlayerStabAttackMixin {
    @Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
    private void mainmod$guardClaimedEntityStab(EquipmentSlot slot, Entity target, float baseDamage,
                                                 boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!ClaimsManager.allowEntityAttack((Player) (Object) this, target)) cir.setReturnValue(false);
    }
}
