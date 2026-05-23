package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public class EnduranceCharm implements Charm
{
    private static final float EXHAUSTION_REDUCTION_PER_LEVEL = 0.15F;
    private static final int MAX_EFFECTIVE_LEVEL = 5;

    public static float reduceNonCombatExhaustion(Player player, float exhaustion)
    {
        if (!(player instanceof ServerPlayer serverPlayer)) return exhaustion;

        int charmLevel = CharmsManager.getPlayerCharmLevel(serverPlayer, EnduranceCharm.class);
        if (charmLevel <= 0) return exhaustion;

        int effectiveLevel = Math.min(charmLevel, MAX_EFFECTIVE_LEVEL);
        return exhaustion * (1.0F - (EXHAUSTION_REDUCTION_PER_LEVEL * effectiveLevel));
    }
}
