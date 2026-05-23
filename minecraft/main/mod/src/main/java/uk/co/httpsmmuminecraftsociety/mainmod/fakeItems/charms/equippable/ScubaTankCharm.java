package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class ScubaTankCharm implements Charm, EquippedTickCallbackCharm
{
    private static final double STATIONARY_HORIZONTAL_SPEED_SQUARED = 0.0001;

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        if (!player.isUnderWater()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() > STATIONARY_HORIZONTAL_SPEED_SQUARED) return;

        int airSupply = player.getAirSupply();
        int maxAirSupply = player.getMaxAirSupply();
        if (airSupply >= maxAirSupply) return;

        int refundTicksPerFive = Math.min(Math.max(charmLevel, 0), 5);
        if (refundTicksPerFive <= 0) return;
        if (level.getGameTime() % 5 >= refundTicksPerFive) return;

        player.setAirSupply(Math.min(airSupply + 1, maxAirSupply));
    }
}
