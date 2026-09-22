package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.HappyGhastSpeedCharm;

@Mixin(HappyGhast.class)
public final class HappyGhastRiddenSpeedMixin
{
    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "happy_ghast_speed_charm");

    @Inject(method = "tick", at = @At("HEAD"))
    private void mainmod$applyRiderCharmSpeed(CallbackInfo callback) {
        HappyGhast ghast = (HappyGhast) (Object) this;
        if (ghast.level().isClientSide()) return;

        AttributeInstance speed = ghast.getAttribute(Attributes.FLYING_SPEED);
        if (speed == null) return;

        int charmLevel = ghast.getControllingPassenger() instanceof ServerPlayer rider
                ? CharmsManager.getPlayerCharmLevel(rider, HappyGhastSpeedCharm.class)
                : 0;
        if (charmLevel <= 0) {
            speed.removeModifier(SPEED_MODIFIER_ID);
            return;
        }

        double amount = HappyGhastSpeedCharm.flyingSpeedModifierForLevel(charmLevel);
        AttributeModifier current = speed.getModifier(SPEED_MODIFIER_ID);
        if (current == null || current.amount() != amount) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }
}
