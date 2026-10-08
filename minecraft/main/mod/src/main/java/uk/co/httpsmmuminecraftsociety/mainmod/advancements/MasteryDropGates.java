package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.resources.Identifier;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class MasteryDropGates {
    private static final String[] DROPS = {"welcoming", "soaring", "imaginative", "nether", "circus",
            "inventors", "beach-party", "void", "unthemed", "cold", "jolly", "christmas"};
    private MasteryDropGates() {}

    public static int enabledMask() {
        int mask = 0;
        for (int i = 0; i < DROPS.length; i++) {
            if (FeatureToggles.isEnabled(DROPS[i])) mask |= 1 << i;
        }
        return mask;
    }

    public static boolean isUnlocked(Identifier id) {
        if (!id.getNamespace().equals("mainmod") || !id.getPath().startsWith("mastery/")) return true;
        String drop = dropFor(id.getPath().substring("mastery/".length()));
        return drop.isEmpty() || FeatureToggles.isEnabled(drop);
    }

    static String dropFor(String path) {
        if (path.startsWith("cosmetics/item/")) {
            FakeItem item = FakeItems.ID_MAP.get("cosmetic-" + path.substring("cosmetics/item/".length()));
            return item == null ? "" : item.shopDrop();
        }
        if (path.startsWith("glider/")) return FeatureToggles.SOARING;
        if (path.startsWith("enderite/")) return FeatureToggles.VOID;
        if (path.startsWith("sniffers/")) return FeatureToggles.WELCOMING;
        return switch (path) {
            case "utility/slime_detector", "utility/slime_chunk", "utility/no_slime_bars",
                    "cosmetics/good_boy", "cosmetics/cat_stack", "cosmetics/crown_royal",
                    "cosmetics/trans_bee_hood" -> FeatureToggles.WELCOMING;
            case "utility/sculk_phial_extract", "utility/sculk_phial_full", "utility/particle_trail",
                    "cosmetics/retro_helmet", "potions/insomnia" -> FeatureToggles.SOARING;
            case "utility/wrench", "utility/invisible_frame", "utility/decoblock" -> "imaginative";
            case "cosmetics/sombrero", "utility/max_reach_beacon" -> FeatureToggles.NETHER;
            case "utility/golden_paste", "potions/impersonation", "cosmetics/sombreron" -> FeatureToggles.CIRCUS;
            case "cosmetics/sombreronn", "cosmetics/solar_eclipse", "cosmetics/all_straw_hats" -> "inventors";
            case "cosmetics/posh_squid" -> "beach-party";
            default -> "";
        };
    }
}
