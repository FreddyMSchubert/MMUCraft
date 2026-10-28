package uk.co.httpsmmuminecraftsociety.mainmod.thunder;

import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;

public final class ThunderRitualCheck {
    public static void main(String[] args) {
        Bootstrap.bootStrap();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) == 2 && Math.abs(z) < 2)
                assert ThunderRitual.inwardFacing(x, z) == (x < 0 ? Direction.EAST : Direction.WEST);
            if (Math.abs(z) == 2 && Math.abs(x) < 2)
                assert ThunderRitual.inwardFacing(x, z) == (z < 0 ? Direction.SOUTH : Direction.NORTH);
        }
        assert ThunderRitual.cornerOffset(-0.1) == -2;
        assert ThunderRitual.cornerOffset(0.1) == 2;
        assert ThunderRitual.moonDay(0) == 0;
        assert ThunderRitual.moonDay(7 * 24_000L) == 7;
        assert ThunderRitual.moonDay(8 * 24_000L) == 0;
        assert ThunderRitual.countWord(1).equals("one");
        assert ThunderRitual.countWord(10).equals("ten");
        assert ThunderRitual.countWord(11).equals("11");
    }
}
