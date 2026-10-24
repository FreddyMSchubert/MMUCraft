package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.GoopHandCharm;

@Mixin(LivingEntity.class)
public abstract class GoopHandMobKnockbackMixin {
    @ModifyVariable(method = "getKnockback", at = @At(value = "STORE"), ordinal = 0)
    private float mainmod$goopHandOnlyAffectsMobs(float knockback, Entity target, DamageSource source) {
        if (target instanceof Mob || !((Object) this instanceof ServerPlayer player)) return knockback;

        int level = CharmsManager.getPlayerCharmLevel(player, GoopHandCharm.class);
        return knockback - GoopHandCharm.knockbackBonus(level);
    }
}
