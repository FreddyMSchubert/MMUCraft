package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

public record DailyTaskEvent(Type type, String subject, String secondary, int amount) {
    public DailyTaskEvent {
        if (amount < 1) throw new IllegalArgumentException("Daily event amounts must be positive");
        subject = subject == null ? "" : subject;
        secondary = secondary == null ? "" : secondary;
    }

    public static DailyTaskEvent of(Type type) {
        return new DailyTaskEvent(type, "", "", 1);
    }

    public static DailyTaskEvent of(Type type, String subject) {
        return new DailyTaskEvent(type, subject, "", 1);
    }

    public static DailyTaskEvent simple(DailySimpleEvent event) {
        return of(Type.SIMPLE, event.id());
    }

    public static DailyTaskEvent charm(DailyCharm charm) {
        return of(Type.USE_CHARM, charm.id());
    }

    public enum Type {
        ENCHANT_AT_TABLE,
        KILL_ENTITY,
        RECEIVE_EFFECT,
        GAIN_LEVEL,
        BREED_ENTITY,
        FEED_ENTITY,
        VILLAGER_TRADE,
        BREW_POTION,
        BRUSH_BLOCK,
        FISH,
        BREAK_BLOCK,
        SIMPLE,
        USE_ITEM,
        USE_CHARM,
        RIDE_DISTANCE,
        HIT_PLAYER_WITH_PROJECTILE,
        PLANT_CROP,
        CREATE_GOLEM,
        TAKE_DAMAGE,
        PLAY_NOTE_BLOCK,
        USE_BLOCK,
        CRAFT_ITEM,
        PLAY_TIME,
        CURE_ZOMBIE_VILLAGER
    }
}
