package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.ScubaTankCharm;

@Mixin(LivingEntity.class)
public class ScubaTankAirDrainSlowdown
{
    @Shadow
    protected int decreaseAirSupply(int air)
    {
        throw new AssertionError();
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;decreaseAirSupply(I)I"
            )
    )
    private int mainmod$scubaTankReduceStationaryAirUse(LivingEntity entity, int currentAir)
    {
        int vanillaAir = this.decreaseAirSupply(currentAir);
        if (!(entity instanceof ServerPlayer player)) return vanillaAir;
        return ScubaTankCharm.adjustAirSupplyAfterVanillaDecrease(player, currentAir, vanillaAir);
    }
}
