package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public enum FishShapes {
    DEFAULT("mainmod:fish_shadow_default"),
    DEFAULT_ALT("mainmod:fish_shadow_default_alt"),
    DEFAULT_ALT_2("mainmod:fish_shadow_default_alt_2"),
    SMALL("mainmod:fish_shadow_small"),
    TINY("mainmod:fish_shadow_tiny"),
    LARGE("mainmod:fish_shadow_large"),
    WEIRD("mainmod:fish_shadow_weird"),
    OBJECT("mainmod:fish_shadow_object"),
    SNAKE("mainmod:fish_shadow_snake"),
    SQUID("mainmod:fish_shadow_squid"),
    SHARK("mainmod:fish_shadow_shark");

    private final String value;

    FishShapes(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static FishShapes fromJsonValue(String value) {
        String normalized = value.contains(":") ? value : "mainmod:" + value;
        for (FishShapes shape : values()) {
            if (shape.value.equals(normalized) || shape.name().equalsIgnoreCase(value)) {
                return shape;
            }
        }
        throw new IllegalArgumentException("Unknown fish shape: " + value);
    }
}
