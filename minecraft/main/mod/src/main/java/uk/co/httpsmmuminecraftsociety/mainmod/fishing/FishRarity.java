package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public enum FishRarity {
    // Keep Common and the total fixed; split the removed Mythical weight 1:2:3:4 across the middle tiers.
    COMMON("Common", 1, 65.0, 55.0),
    UNCOMMON("Uncommon", 2, 25.000666666667, 22.05),
    RARE("Rare", 3, 5.001333333333, 12.1),
    EPIC("Epic", 4, 0.502, 6.15),
    LEGENDARY("Legendary", 5, 0.102666666667, 4.45),
    MYTHICAL("Mythical", 5, 0.003333333333, 0.25);

    private final String displayName;
    private final int catchLevel;
    private final double zeroLuckWeight;
    private final double maxLuckWeight;

    FishRarity(String displayName, int catchLevel, double zeroLuckWeight, double maxLuckWeight) {
        this.displayName = displayName;
        this.catchLevel = catchLevel;
        this.zeroLuckWeight = zeroLuckWeight;
        this.maxLuckWeight = maxLuckWeight;
    }

    public String displayName() {
        return displayName;
    }

    public int catchLevel() {
        return catchLevel;
    }

    public int colorRgb() {
        return switch (this) {
            case COMMON -> 0xFFFFFF;
            case UNCOMMON -> 0xFFFF55;
            case RARE -> 0x55FFFF;
            case EPIC -> 0xFF55FF;
            case LEGENDARY -> 0x55FF55;
            case MYTHICAL -> 0xFF5F00;
        };
    }

    public double weightAtLuck(double luck) {
        double progress = Math.max(0.0, Math.min(11.0, luck)) / 11.0;
        return zeroLuckWeight + (maxLuckWeight - zeroLuckWeight) * progress;
    }

    public static FishRarity fromJsonValue(String value) {
        return switch (value.toLowerCase()) {
            case "common" -> COMMON;
            case "uncommon" -> UNCOMMON;
            case "rare" -> RARE;
            case "epic" -> EPIC;
            case "legendary" -> LEGENDARY;
            case "mythical" -> MYTHICAL;
            default -> throw new IllegalArgumentException("Unknown fish rarity: " + value);
        };
    }
}
