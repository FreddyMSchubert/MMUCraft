package uk.co.httpsmmuminecraftsociety.mainmod.mixin.exhaustionCharm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.EnduranceCharm;

@Mixin(Block.class)
public class BlockBreakExhaustionMixin
{
    @Redirect(
            method = "playerDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V")
    )
    private void mainmod$reduceBlockBreakExhaustion(ServerPlayer player, float exhaustion) {
        player.causeFoodExhaustion(EnduranceCharm.reduceNonCombatExhaustion(player, exhaustion));
    }
}
