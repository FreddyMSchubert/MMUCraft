package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.RandomSource;

public record FishSize(double averageCm, double deviationCm) {
    private static final double NINETY_NINE_PERCENT_Z_SCORE = 2.58;

    public FishSize {
        if (averageCm <= 0.0 || deviationCm <= 0.0) {
            throw new IllegalArgumentException("Fish length values must be positive");
        }
    }

    public double roll(RandomSource random) {
        // 2.58 standard deviations contain just over 99% of a normal distribution.
        double length = averageCm + random.nextGaussian() * deviationCm / NINETY_NINE_PERCENT_Z_SCORE;
        return Math.max(0.1, Math.round(length * 10.0) / 10.0);
    }
}
