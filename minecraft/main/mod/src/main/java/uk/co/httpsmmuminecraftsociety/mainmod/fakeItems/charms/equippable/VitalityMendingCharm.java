package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public class VitalityMendingCharm implements Charm
{
    private static int xpToHealOneHpPerLevel(int level) {
        if (level <= 0) return Integer.MAX_VALUE;
        return Math.max(1, 6 - level);
    }

    public static int healIfVitalityMending(ServerPlayer player, int xp) {
        int vitalityMendingLevel = CharmsManager.getPlayerCharmLevel(player, VitalityMendingCharm.class);
        int xpToHealOneHp = xpToHealOneHpPerLevel(vitalityMendingLevel);
        while (player.getHealth() < player.getMaxHealth() && xp >= xpToHealOneHp) {
            player.heal(0.5f);
            xp -= xpToHealOneHp;
        }
        return xp;
    }
}
