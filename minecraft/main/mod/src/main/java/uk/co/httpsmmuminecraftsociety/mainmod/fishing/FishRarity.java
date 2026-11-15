package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public enum FishRarity {
    // Endpoint weights. Each higher tier receives a progressively stronger reduction.
    COMMON("Common", 1, 65.0, 55.0),
    UNCOMMON("Uncommon", 2, 25.0, 22.0),
    RARE("Rare", 3, 5.0, 12.0),
    EPIC("Epic", 4, 0.5, 6.0),
    LEGENDARY("Legendary", 5, 0.1, 4.25),
    MYTHICAL("Mythical", 5, 0.01, 0.75);

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

    int requiredCatches() {
        return switch (this) {
            case EPIC -> 100;
            case LEGENDARY -> 300;
            case MYTHICAL -> 500;
            default -> 0;
        };
    }

    static double[] weightsAt(double luck, int catches) {
        FishRarity[] rarities = values();
        double[] weights = new double[rarities.length];
        double zeroTotal = 0.0;
        double maxTotal = 0.0;
        for (FishRarity rarity : rarities) {
            zeroTotal += rarity.zeroLuckWeight;
            maxTotal += rarity.maxLuckWeight;
        }

        double progress = Math.max(0.0, Math.min(11.0, luck)) / 11.0;
        double commonChance = 1.0;
        for (FishRarity rarity : rarities) {
            if (rarity == COMMON || catches < rarity.requiredCatches()) continue;
            double reduction = 1.0 + 2.0 * rarity.ordinal() / (rarities.length - 1);
            double zeroChance = rarity.zeroLuckWeight / zeroTotal / reduction;
            double maxChance = rarity.maxLuckWeight / maxTotal / reduction;
            double chance = zeroChance + (maxChance - zeroChance) * progress;
            weights[rarity.ordinal()] = chance;
            commonChance -= chance;
        }
        weights[COMMON.ordinal()] = commonChance;
        return weights;
    }

    static double[] weightsWithBoost(double luck, int catches, int boostMask) {
        double[] weights = weightsAt(luck, catches);
        if (boostMask == 0) return weights;
        double[] unlocked = catches < MYTHICAL.requiredCatches() && (boostMask & 0b111000) != 0
                ? weightsAt(luck, Integer.MAX_VALUE) : weights;
        double progress = Math.max(0.0, Math.min(11.0, luck)) / 11.0;
        double total = 0.0;
        for (FishRarity rarity : values()) {
            int index = rarity.ordinal();
            if ((boostMask & (1 << index)) != 0) {
                double multiplier = switch (rarity) {
                    case COMMON -> 2.0;
                    case UNCOMMON -> 2.5;
                    case RARE -> 3.0;
                    case EPIC -> 4.0;
                    case LEGENDARY -> 5.0;
                    case MYTHICAL -> 8.0;
                };
                double minimum = switch (rarity) {
                    case EPIC -> 0.04 + 0.04 * progress;
                    case LEGENDARY -> 0.02 + 0.02 * progress;
                    case MYTHICAL -> 0.01 + 0.01 * progress;
                    default -> 0.0;
                };
                double source = weights[index] > 0.0 ? weights[index] : unlocked[index];
                weights[index] = Math.max(multiplier * source, minimum);
            }
            total += weights[index];
        }
        for (int index = 0; index < weights.length; index++) weights[index] /= total;
        return weights;
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
