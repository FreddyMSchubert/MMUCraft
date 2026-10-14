package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class ThickSocksCharm implements Charm {
    public static final int CHARM_ID = 59;

    public static boolean isActive(ItemStack boots) {
        return CharmStackData.getStoredCharms(boots).stream()
                .anyMatch(charm -> charm.charmId() == CHARM_ID && charm.level() > 0);
    }
}
