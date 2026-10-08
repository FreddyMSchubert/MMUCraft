package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.resources.Identifier;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyAdvancementPolicy;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class AdvancementDropGates {
    private AdvancementDropGates() {}

    public static boolean isUnlocked(Identifier id, Identifier rootId) {
        if (!MasteryDropGates.isUnlocked(id)) return false;
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (namespace.equals("minecraft") || namespace.equals("blazeandcave")) {
            if (path.startsWith("nether/") && !FeatureToggles.isEnabled(FeatureToggles.NETHER)) return false;
            if (path.startsWith("end/") && !FeatureToggles.isEnabled(FeatureToggles.VOID)) return false;
            if (namespace.equals("blazeandcave") && path.startsWith("potion/")
                    && !FeatureToggles.isEnabled(FeatureToggles.NETHER)) return false;
            return DailyAdvancementPolicy.isDropUnlocked(id, rootId);
        }
        return true;
    }
}
