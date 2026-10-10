package dev.freddy.killshift.mixin;

import dev.freddy.killshift.ShapeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
abstract class BlazeSnowballMixin {
    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void killshift$damageBlazeForm(EntityHitResult hit, CallbackInfo callback) {
        if (hit.getEntity() instanceof ServerPlayer player && ShapeManager.isForm(player, EntityTypes.BLAZE)
                && player.level() instanceof ServerLevel level) {
            Snowball snowball = (Snowball) (Object) this;
            player.hurtServer(level, snowball.damageSources().thrown(snowball, snowball.getOwner()), 3.0F);
        }
    }
}
