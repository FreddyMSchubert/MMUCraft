package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.RandomSource;

public final class FishingCheck {
    private FishingCheck() {}

    public static void main(String[] args) {
        checkWaitTiming();
        checkDistanceNormalizedApproach();
        checkFastFishFirstBobChance();
        checkPhysicalShadowScale();
        System.out.println("Fishing checks passed: waits, approach speed, first-bob chance, and shadow scale.");
    }

    private static void checkWaitTiming() {
        int[][] expectedRanges = {
                {400, 560},
                {280, 428},
                {160, 294},
                {40, 160}
        };
        RandomSource random = RandomSource.create(219L);
        for (int lureLevel = 0; lureLevel <= 3; lureLevel++) {
            int observedMinimum = Integer.MAX_VALUE;
            int observedMaximum = Integer.MIN_VALUE;
            for (int roll = 0; roll < 10_000; roll++) {
                int ticks = AnimalCrossingFishingTiming.initialWaitTicks(random, lureLevel);
                observedMinimum = Math.min(observedMinimum, ticks);
                observedMaximum = Math.max(observedMaximum, ticks);
            }
            assert observedMinimum == expectedRanges[lureLevel][0];
            assert observedMaximum == expectedRanges[lureLevel][1];
        }
    }

    private static void checkDistanceNormalizedApproach() {
        FishingPersonality personality = personality(3.0F, 8.0F, FishShapes.DEFAULT);
        assert personality.approachTicks(1.0D) == 10;
        assert personality.approachTicks(5.0D) == 50;
        assert personality.approachTicks(0.05D) == 1;
    }

    private static void checkFastFishFirstBobChance() {
        FishingPersonality fast = personality(1.0F, 4.99F, FishShapes.DEFAULT);
        RandomSource random = RandomSource.create(219L);
        int firstBobBites = 0;
        for (int roll = 0; roll < 10_000; roll++) {
            int bounces = AnimalCrossingFishingTiming.rollBounceCount(random, fast);
            if (bounces == 1) {
                firstBobBites++;
            } else {
                assert bounces >= 2;
            }
        }
        assert firstBobBites >= 4_500 && firstBobBites <= 5_500;

        FishingPersonality threshold = personality(1.0F, 5.0F, FishShapes.DEFAULT);
        for (int roll = 0; roll < 100; roll++) {
            assert AnimalCrossingFishingTiming.rollBounceCount(random, threshold) == 1;
        }
    }

    private static void checkPhysicalShadowScale() {
        for (FishShapes shape : FishShapes.values()) {
            float scale = shape.shadowScale(100.0D);
            assert Math.abs(shape.shadowLengthBlocks(scale) - 2.0D) < 0.000001D;
        }
    }

    private static FishingPersonality personality(
            float averageBounces,
            float averageCatchSeconds,
            FishShapes shape
    ) {
        return new FishingPersonality(
                FishRarity.COMMON,
                1.0F,
                shape.value(),
                shape.shadowScale(50.0D),
                0.2F,
                0.5F,
                1.0F,
                1.0F,
                averageBounces,
                averageCatchSeconds
        );
    }
}
