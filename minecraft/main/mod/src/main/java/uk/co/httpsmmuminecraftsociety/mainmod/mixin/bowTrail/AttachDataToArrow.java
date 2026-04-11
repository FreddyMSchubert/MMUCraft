package uk.co.httpsmmuminecraftsociety.mainmod.mixin.bowTrail;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.ArrowTrailAccess;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.BowTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.WeightedTrailSpec;

@Mixin(BowItem.class)
public abstract class AttachDataToArrow
{
    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void mainmod$copyTrailDataToArrow(
            LivingEntity shooter,
            Projectile projectile,
            int index,
            float speed,
            float divergence,
            float yaw,
            LivingEntity target,
            CallbackInfo ci
    ) {
        if (!(projectile instanceof AbstractArrow arrow)) {
            return;
        }

        ItemStack bowStack = BowTrailData.findBowStack(shooter);
        if (bowStack.isEmpty()) {
            return;
        }

        WeightedTrailSpec spec = BowTrailData.getTrailSpec(bowStack);
        if (spec.isEmpty()) {
            return;
        }

        ((ArrowTrailAccess) arrow).mainmod$setTrailSpec(spec);

        arrow.setCritArrow(false);
    }
}