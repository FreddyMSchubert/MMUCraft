package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.RandomSource;

public final class FishingBounceCheck {
    private FishingBounceCheck() {}

    public static void main(String[] args) {
        double[] bites = new double[11];
        double firstSpeciesMean = 0.0D;
        double lastSpeciesMean = 0.0D;
        for (int rank = 0; rank < 134; rank++) {
            double percentile = rank / 133.0D;
            float tendency = percentile <= 0.95D
                    ? (float) (1.25D + 8.75D * percentile / 0.95D)
                    : (float) (10.0D + 4.0D * (percentile - 0.95D) / 0.05D);
            FishingPersonality personality = new FishingPersonality(
                    FishRarity.COMMON, 1.0F, 0.0F, false, 16.0F,
                    1.0F, 0.2F, 0.5F, 1.0F, 0.75F, tendency);
            double surviving = 1.0D;
            double speciesMean = 0.0D;
            for (int contact = 1; contact <= 10; contact++) {
                double bitten = surviving * AnimalCrossingFishingTiming.biteChance(personality, contact);
                bites[contact] += bitten / 134.0D;
                speciesMean += contact * bitten;
                surviving -= bitten;
            }
            assert surviving < 0.000001D;
            if (rank == 0) firstSpeciesMean = speciesMean;
            if (rank == 133) lastSpeciesMean = speciesMean;
        }

        assert bites[4] > bites[3] && bites[4] > bites[5];
        assert Math.abs(bites[1] / bites[4] - 0.5D) < 0.02D;
        for (int contact = 1; contact <= 3; contact++) {
            assert Math.abs(bites[contact] - bites[8 - contact]) < 0.002D;
        }
        assert bites[10] < 0.005D;
        assert lastSpeciesMean > firstSpeciesMean + 3.0D;

        RandomSource random = RandomSource.create(42L);
        FishingPersonality slowFish = new FishingPersonality(
                FishRarity.COMMON, 1.0F, 0.0F, false, 16.0F,
                1.0F, 0.2F, 0.5F, 1.0F, 0.75F, 14.0F);
        for (int cast = 0; cast < 1_000; cast++) {
            int contact = 1;
            while (!AnimalCrossingFishingTiming.shouldBite(random, slowFish, contact)) contact++;
            assert contact <= 10;
        }
    }
}
