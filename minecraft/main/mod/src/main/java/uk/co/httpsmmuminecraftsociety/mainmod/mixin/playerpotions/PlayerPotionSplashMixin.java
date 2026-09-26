package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerPotions;

@Mixin(ThrownSplashPotion.class)
abstract class PlayerPotionSplashMixin {
    @Inject(method = "onHitAsPotion", at = @At("TAIL"))
    private void mainmod$splash(ServerLevel level, ItemStack stack, HitResult hit, CallbackInfo ci) {
        String identity = PlayerPotions.identity(stack);
        if (identity == null) return;
        ThrownSplashPotion potion = (ThrownSplashPotion) (Object) this;
        AABB hitBox = potion.getBoundingBox().move(hit.getLocation().subtract(potion.position()));
        AABB area = hitBox.inflate(4.0, 2.0, 4.0);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (player.isAffectedByPotions() && hitBox.distanceToSqr(player.getBoundingBox()) < 16.0) {
                PlayerDisguises.apply(player, identity, PlayerPotions.duration(identity));
            }
        }
    }
}
