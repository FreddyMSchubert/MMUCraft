package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.VitalityMendingCharm;

@Mixin(ExperienceOrb.class)
public abstract class XPOrbMendVitality
{
    @Shadow
    protected abstract int repairPlayerItems(ServerPlayer player, int amount);

    @Redirect(
            method = "playerTouch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/server/level/ServerPlayer;I)I"
            )
    )
    private int mainmod$redirectRepairPlayerItems(
            ExperienceOrb orb,
            ServerPlayer player,
            int orbValue
    ) {
        int remainingXp = VitalityMendingCharm.healIfVitalityMending(player, orbValue);
        return this.repairPlayerItems(player, remainingXp);
    }
}
