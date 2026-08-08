package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import java.util.Locale;

public enum DailySimpleEvent {
    SHEAR_SHEEP,
    IGNITE_CREEPER,
    REFLECT_GHAST_FIREBALL,
    JUMP_SLIME_BLOCK,
    DEFEAT_RAID,
    LIGHT_TNT,
    RENAME_TOOL,
    LIGHT_CANDLE,
    MILK_COW,
    BRUSH_ARMADILLO,
    PLAY_MUSIC_DISC,
    RING_BELL,
    FILL_FLOWER_POT,
    HANG_PAINTING,
    FILL_BOOKSHELF,
    READ_NEW_JOKE,
    KICK_SULFUR_CUBE,
    CUSTOMIZE_BANNER,
    EYE_CONTACT_ENDERMAN,
    MODIFY_ITEM_FRAME;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
