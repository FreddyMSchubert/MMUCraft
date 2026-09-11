package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.RandomSource;

public final class FishingCheck {
    private FishingCheck() {}

    public static void main(String[] args) {
        checkWaitTiming();
        checkDistanceNormalizedApproach();
        checkFastFishFirstBobChance();
        checkPhysicalShadowScale();
        checkLureBookShortcutChance();
        checkTextureRotation();
        System.out.println("Fishing checks passed: waits, approach speed, lure books, rotations, first-bob chance, and shadow scale.");
    }

    private static void checkWaitTiming() {
        int[][] expectedRanges = {
                {400, 560},
                {280, 428},
                {160, 294},
                {40, 200}
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

    private static void checkTextureRotation() {
        assert FishTextureAngle.parse("TR") == 45.0F;
        assert FishTextureAngle.parse("BL") == 225.0F;
        FishingPersonality fixed = personality(3.0F, 8.0F);
        assert fixed.resolveTextureAngle(RandomSource.create(219L)) == fixed;

        FishingPersonality random = new FishingPersonality(
                fixed.rarity(), fixed.struggleSeconds(), fixed.textureAngleDegrees(), true,
                fixed.textureLengthPixels(), fixed.size(), fixed.secondsAwayFromBobber(),
                fixed.approachSeconds(), fixed.retreatSeconds(), fixed.retreatDistance(),
                fixed.averageBounces(), fixed.averageCatchSeconds()
        );
        FishingPersonality resolved = random.resolveTextureAngle(RandomSource.create(219L));
        assert !resolved.randomTextureAngle();
        assert resolved.textureAngleDegrees() >= 0.0F && resolved.textureAngleDegrees() < 360.0F;
    }

    private static void checkLureBookShortcutChance() {
        assert AnimalCrossingFishingTiming.lureBookShortcutChance(0) == 0.25D;
        assert AnimalCrossingFishingTiming.lureBookShortcutChance(1) == 0.125D;
        assert AnimalCrossingFishingTiming.lureBookShortcutChance(2) == 0.05D;
        assert AnimalCrossingFishingTiming.lureBookShortcutChance(3) == 0.0D;
    }

    private static void checkDistanceNormalizedApproach() {
        FishingPersonality personality = personality(3.0F, 8.0F);
        assert personality.approachTicks(1.0D) == 10;
        assert personality.approachTicks(5.0D) == 50;
        assert personality.approachTicks(0.05D) == 1;
    }

    private static void checkFastFishFirstBobChance() {
        FishingPersonality fast = personality(1.0F, 4.99F);
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

        FishingPersonality threshold = personality(1.0F, 5.0F);
        for (int roll = 0; roll < 100; roll++) {
            assert AnimalCrossingFishingTiming.rollBounceCount(random, threshold) == 1;
        }
    }

    private static void checkPhysicalShadowScale() {
        assert Math.abs(FishSize.blocks(100.0D) - 2.0D) < 0.000001D;
        assert FishingCatches.FISH_SHADOW_BASE_SCALE == 1.0F;
        assert FishingCatches.ITEM_SHADOW_SCALE == 0.64F;
    }

    private static FishingPersonality personality(
            float averageBounces,
            float averageCatchSeconds
    ) {
        return new FishingPersonality(
                FishRarity.COMMON,
                1.0F,
				270.0F,
				false,
				16.0F,
                FishSize.blocks(50.0D),
                0.2F,
                0.5F,
                1.0F,
                1.0F,
                averageBounces,
                averageCatchSeconds
        );
    }
}
