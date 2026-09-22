package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class HappyGhastSpeedCharm implements Charm
{
    public static final int CHARM_ID = 56;
    public static final int MIN_SPEED_BLOCKS_PER_SECOND = 4;
    public static final int MAX_SPEED_BLOCKS_PER_SECOND = 10;

    private static final double VANILLA_SPEED_BLOCKS_PER_SECOND = 3.6D;

    public static int speedForLevel(int charmLevel)
    {
        return Math.clamp(
                MIN_SPEED_BLOCKS_PER_SECOND + charmLevel - 1,
                MIN_SPEED_BLOCKS_PER_SECOND,
                MAX_SPEED_BLOCKS_PER_SECOND
        );
    }

    public static double flyingSpeedModifierForLevel(int charmLevel)
    {
        return Math.sqrt(speedForLevel(charmLevel) / VANILLA_SPEED_BLOCKS_PER_SECOND) - 1.0D;
    }
}
