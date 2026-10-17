package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class ReapingGloveCharm implements Charm {
    public static final int CHARM_ID = 60;
    public static double multiplier(int level) {
        return level <= 0 ? 1 : (13 + 2 * Math.min(level, 20)) / 10.0;
    }
}
