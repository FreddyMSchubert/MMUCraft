package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;

public final class EndFishingAnchor extends Display.ItemDisplay {
    public EndFishingAnchor(ServerLevel level) {
        super(EntityTypes.ITEM_DISPLAY, level);
        setNoGravity(true);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
