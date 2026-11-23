package uk.co.httpsmmuminecraftsociety.mainmod.mixin.bigboss;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.bigboss.BigBossManager;

@Mixin(LivingEntity.class)
public abstract class BigBossLocatorBarMixin {
    private static final double PLAYER_WAYPOINT_RANGE = 60_000_000.0;

    @Inject(method = "getAttributeValue", at = @At("HEAD"), cancellable = true)
    private void keepBossWaypointVisible(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (attribute.is(Attributes.WAYPOINT_TRANSMIT_RANGE)
                && (Object) this instanceof ServerPlayer player
                && BigBossManager.isBoss(player)) {
            cir.setReturnValue(PLAYER_WAYPOINT_RANGE);
        }
    }

    @Inject(method = "onAttributeUpdated", at = @At("TAIL"))
    private void restoreBossWaypoint(Holder<Attribute> attribute, CallbackInfo ci) {
        if (attribute.is(Attributes.WAYPOINT_TRANSMIT_RANGE)
                && (Object) this instanceof ServerPlayer player
                && BigBossManager.isBoss(player)) {
            player.level().getWaypointManager().trackWaypoint(player);
        }
    }
}
