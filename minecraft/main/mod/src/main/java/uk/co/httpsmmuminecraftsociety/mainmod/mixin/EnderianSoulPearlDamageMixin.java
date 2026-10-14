package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.EnderianSoulCharm;

@Mixin(ThrownEnderpearl.class)
public abstract class EnderianSoulPearlDamageMixin {
    @Redirect(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mainmod$avoidPearlDamage(ServerPlayer player, ServerLevel level, DamageSource source, float amount) {
        int chance = EnderianSoulCharm.damageAvoidPercent(
                CharmsManager.getPlayerCharmLevel(player, EnderianSoulCharm.class));
        if (player.getRandom().nextInt(100) < chance) return false;
        return player.hurtServer(level, source, amount);
    }
}
