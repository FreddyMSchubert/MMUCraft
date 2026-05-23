package uk.co.httpsmmuminecraftsociety.mainmod.mixin.exhaustionCharm;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.EnduranceCharm;

@Mixin(ServerPlayer.class)
public class ServerPlayerExhaustionMixin
{
    @ModifyArg(
            method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V")
    )
    private float mainmod$reduceMovementExhaustion(float exhaustion)
    {
        return EnduranceCharm.reduceNonCombatExhaustion((ServerPlayer) (Object) this, exhaustion);
    }

    @ModifyArg(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V")
    )
    private float mainmod$reduceJumpExhaustion(float exhaustion)
    {
        return EnduranceCharm.reduceNonCombatExhaustion((ServerPlayer) (Object) this, exhaustion);
    }
}
