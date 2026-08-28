package uk.co.httpsmmuminecraftsociety.mainmod.toggles;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

public final class DimensionAccess {
    private DimensionAccess() {}

    public static boolean allows(Entity entity, TeleportTransition transition) {
        var origin = entity.level().dimension();
        var destination = transition.newLevel().dimension();
        if (destination.equals(Level.NETHER) && !origin.equals(Level.NETHER)) {
            return FeatureToggles.isEnabled(FeatureToggles.NETHER);
        }
        return !destination.equals(Level.END)
                || origin.equals(Level.END)
                || FeatureToggles.isEnabled(FeatureToggles.END);
    }
}
