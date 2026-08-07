package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfDisplacementCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfInsomniaCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfResonanceCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfReturningCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;

public enum DailyCharm {
    DISPLACEMENT(PotionOfDisplacementCharm.class, "a Potion of Displacement"),
    RETURNING(PotionOfReturningCharm.class, "a Potion of Returning"),
    RESONANCE(PotionOfResonanceCharm.class, "a Potion of Resonance"),
    INSOMNIA(PotionOfInsomniaCharm.class, "a Potion of Insomnia");

    private final Class<? extends Charm> charmClass;
    private final String displayName;

    DailyCharm(Class<? extends Charm> charmClass, String displayName) {
        this.charmClass = charmClass;
        this.displayName = displayName;
    }

    public String id() {
        var matches = FakeItems.ALL.stream()
                .filter(item -> {
                    CharmItemFeature feature = item.getFeature(CharmItemFeature.class);
                    return feature != null && feature.charm().getClass() == charmClass;
                })
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected one fake item for daily charm " + name() + ", found " + matches.size());
        }
        return "mainmod:" + matches.getFirst().id();
    }

    public String displayName() {
        return displayName;
    }

    public static DailyCharm from(Charm charm) {
        for (DailyCharm value : values()) {
            if (value.charmClass == charm.getClass()) return value;
        }
        return null;
    }
}
