package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureToggle;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.Map;

public final class MasteryDropGatesCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Identifier glider = Identifier.parse("mainmod:mastery/glider/craft");
        Identifier wrench = Identifier.parse("mainmod:mastery/utility/wrench");
        Identifier vanilla = Identifier.parse("minecraft:story/root");

        assert !MasteryDropGates.isUnlocked(glider);
        assert !MasteryDropGates.isUnlocked(wrench);
        assert MasteryDropGates.isUnlocked(vanilla);

        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("soaring").setEnabled(true)).build());
        int mask = MasteryDropGates.enabledMask();
        assert MasteryDropGates.isUnlocked(glider);
        assert !MasteryDropGates.isUnlocked(wrench);
        assert !MasteryDropGates.isUnlocked(Identifier.parse("mainmod:mastery/enderite/scrap"));
        FakeItems.ID_MAP = Map.of("cosmetic-shulker", new FakeItem("Shulker", "cosmetic-shulker",
                Rarity.COMMON, 1, false, true, "void", List.of(), Items.CARVED_PUMPKIN, List.of()));
        assert !MasteryDropGates.isUnlocked(Identifier.parse("mainmod:mastery/cosmetics/item/shulker"));
        Identifier nether = Identifier.parse("minecraft:nether/root");
        Identifier end = Identifier.parse("blazeandcave:end/root");
        Identifier bacNether = Identifier.parse("blazeandcave:nether/root");
        Identifier potions = Identifier.parse("blazeandcave:potion/root");
        assert !AdvancementDropGates.isUnlocked(nether, nether);
        assert !AdvancementDropGates.isUnlocked(bacNether, bacNether);
        assert !AdvancementDropGates.isUnlocked(potions, potions);
        assert !AdvancementDropGates.isUnlocked(end, end);
        assert !AdvancementDropGates.isUnlocked(Identifier.parse("minecraft:end/root"), Identifier.parse("minecraft:end/root"));
        assert AdvancementDropGates.isUnlocked(vanilla, vanilla);

        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("nether").setEnabled(true)).build());
        assert AdvancementDropGates.isUnlocked(nether, nether);
        assert AdvancementDropGates.isUnlocked(bacNether, bacNether);
        assert AdvancementDropGates.isUnlocked(potions, potions);
        assert !AdvancementDropGates.isUnlocked(end, end);
        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("nether").setEnabled(true))
                .addToggles(FeatureToggle.newBuilder().setKey("void").setEnabled(true)).build());
        assert AdvancementDropGates.isUnlocked(end, end);
        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("soaring").setEnabled(true)).build());
        assert MasteryDropGates.enabledMask() == mask;
        System.out.println("Advancement drop gate checks passed.");
    }
}
