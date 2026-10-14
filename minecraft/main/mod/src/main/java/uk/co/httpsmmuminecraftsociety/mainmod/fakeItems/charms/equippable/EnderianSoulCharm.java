package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class EnderianSoulCharm implements Charm {
    public static final int CHARM_ID = 61;
    private static final int[] DAMAGE_AVOID_PERCENT = {0, 25, 40, 52, 63, 72, 79, 85, 90};
    private static final int[] PEARL_SAVE_PERCENT = {0, 25, 38, 50, 61, 70, 78, 85, 90};

    public static int damageAvoidPercent(int level) {
        return DAMAGE_AVOID_PERCENT[Math.clamp(level, 0, DAMAGE_AVOID_PERCENT.length - 1)];
    }

    public static int pearlSavePercent(int level) {
        return PEARL_SAVE_PERCENT[Math.clamp(level, 0, PEARL_SAVE_PERCENT.length - 1)];
    }
}
