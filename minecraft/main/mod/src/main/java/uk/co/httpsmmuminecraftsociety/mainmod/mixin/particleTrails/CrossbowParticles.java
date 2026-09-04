package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;

@Mixin(CrossbowItem.class)
public abstract class CrossbowParticles {
    @Inject(method = "createProjectile", at = @At("RETURN"))
    private void mainmod$copyFireworkTrail(Level level, LivingEntity shooter, ItemStack weapon,
                                         ItemStack ammunition, boolean critical, CallbackInfoReturnable<Projectile> cir) {
        if (cir.getReturnValue() instanceof FireworkRocketEntity rocket) {
            // The rocket owns this stack. Vanilla saves it with the projectile.
            ParticleTrailData.setTrailSpec(rocket.getItem(), ParticleTrailData.getTrailSpec(weapon));
        }
    }
}
