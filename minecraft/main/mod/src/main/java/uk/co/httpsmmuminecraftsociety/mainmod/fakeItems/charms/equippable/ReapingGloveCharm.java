package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class ReapingGloveCharm implements Charm {
    public static final int CHARM_ID = 60;
    private static final double[] MULTIPLIERS = {1, 1.5, 2, 2.5, 3, 4, 5, 7, 10, 15, 20};

    public static double multiplier(int level) {
        return MULTIPLIERS[Math.clamp(level, 0, MULTIPLIERS.length - 1)];
    }
}
