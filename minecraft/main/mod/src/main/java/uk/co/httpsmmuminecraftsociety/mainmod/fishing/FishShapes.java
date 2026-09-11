package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import java.util.Map;

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

    private static final int FULL_TEXTURE_LENGTH_PIXELS = 19;
    private static final double CENTIMETERS_PER_BLOCK = 50.0D;
    // Visible silhouette widths inside the texture; kept explicit so each shape can be retuned with its art.
    private static final Map<FishShapes, Integer> TEXTURE_LENGTH_PIXELS = Map.ofEntries(
            Map.entry(DEFAULT, 19),
            Map.entry(DEFAULT_ALT, 17),
            Map.entry(DEFAULT_ALT_2, 18),
            Map.entry(SMALL, 9),
            Map.entry(TINY, 6),
            Map.entry(LARGE, 19),
            Map.entry(WEIRD, 11),
            Map.entry(OBJECT, 10),
            Map.entry(SNAKE, 19),
            Map.entry(SQUID, 19),
            Map.entry(SHARK, 19)
    );

    private final String value;

    FishShapes(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public float shadowScale(double lengthCm) {
        double lengthBlocks = lengthCm / CENTIMETERS_PER_BLOCK;
        return (float) (lengthBlocks * FULL_TEXTURE_LENGTH_PIXELS / TEXTURE_LENGTH_PIXELS.get(this));
    }

    public double shadowLengthBlocks(float shadowScale) {
        return shadowScale * TEXTURE_LENGTH_PIXELS.get(this) / FULL_TEXTURE_LENGTH_PIXELS;
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
