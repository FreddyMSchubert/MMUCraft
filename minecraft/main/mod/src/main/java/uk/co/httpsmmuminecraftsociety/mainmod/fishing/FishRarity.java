package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public enum FishRarity {
    COMMON("Common", 1),
    UNCOMMON("Uncommon", 2),
    RARE("Rare", 3),
    EPIC("Epic", 4),
    LEGENDARY("Legendary", 5),
    MYTHICAL("Mythical", 5);

    private final String displayName;
    private final int catchLevel;

    FishRarity(String displayName, int catchLevel) {
        this.displayName = displayName;
        this.catchLevel = catchLevel;
    }

    public String displayName() {
        return displayName;
    }

    public int catchLevel() {
        return catchLevel;
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
