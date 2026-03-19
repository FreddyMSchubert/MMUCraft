package uk.co.httpsmmuminecraftsociety.mainmod.itemdata;

import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.CraftingStaffCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.EnderChestStaffCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.PotionOfReturningCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.UmbrellaCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.BunnyPajamasCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.CandleOfTheDeepCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.CaveSpiderPajamasCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.ExtendoGripCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.GiantsBootsCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.GoopHandCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.HeartCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.HikingBootsCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.KittyPajamasCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.LeprechaunBootsCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.MermaidScalesCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.RunningShoesCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.SpiderPajamasCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.StriderShalesCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.WingedShoesCharm;

import java.util.Map;

public final class CharmCodeRegistry {
    private CharmCodeRegistry() {}

    private static final Map<Integer, Charm> CHARMS = Map.ofEntries(
            Map.entry(1, new CraftingStaffCharm()),
            Map.entry(2, new EnderChestStaffCharm()),
            Map.entry(3, new HeartCharm(0)),
            Map.entry(4, new HeartCharm(1)),
            Map.entry(5, new HeartCharm(2)),
            Map.entry(6, new HeartCharm(3)),
            Map.entry(7, new RunningShoesCharm()),
            Map.entry(8, new CandleOfTheDeepCharm()),
            Map.entry(9, new HikingBootsCharm(0)),
            Map.entry(10, new HikingBootsCharm(1)),
            Map.entry(11, new HikingBootsCharm(2)),
            Map.entry(12, new GiantsBootsCharm()),
            Map.entry(13, new LeprechaunBootsCharm()),
            Map.entry(14, new MermaidScalesCharm()),
            Map.entry(15, new StriderShalesCharm()),
            Map.entry(16, new ExtendoGripCharm()),
            Map.entry(17, new BunnyPajamasCharm()),
            Map.entry(18, new KittyPajamasCharm()),
            Map.entry(19, new SpiderPajamasCharm()),
            Map.entry(20, new CaveSpiderPajamasCharm()),
            Map.entry(21, new GoopHandCharm()),
            Map.entry(22, new WingedShoesCharm(0)),
            Map.entry(23, new WingedShoesCharm(1)),
            Map.entry(24, new WingedShoesCharm(2)),
            Map.entry(25, new PotionOfReturningCharm()),
            Map.entry(26, new UmbrellaCharm())
    );

    public static Charm getRequired(int effectId, String sourcePath) {
        Charm charm = CHARMS.get(effectId);
        if (charm == null) {
            throw new IllegalStateException(
                    "Unknown charm effectId " + effectId + " in " + sourcePath + ". " +
                    "Add it to CharmCodeRegistry before using it in JSON."
            );
        }
        return charm;
    }
}
