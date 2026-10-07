package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class FishingCheck {
    private FishingCheck() {}

    public static void main(String[] args) {
        checkWaitTiming();
        checkCatchWindows();
        checkDistanceNormalizedApproach();
        checkFastFishFirstBobChance();
        checkPhysicalShadowScale();
        checkLureBookShortcutChance();
        checkCatchGates();
        checkWeightedFishSelection();
        checkTextureRotation();
        System.out.println("Fishing checks passed: waits, catch windows, catch gates, weighted fish, approach speed, lure books, rotations, first-bob chance, and shadow scale.");
    }

    private static void checkCatchWindows() {
        int[] expectedTicks = {16, 15, 13, 12, 10, 10};
        FishRarity[] rarities = FishRarity.values();
        for (int i = 0; i < rarities.length; i++) {
            assert personality(3.0F, 8.0F, rarities[i]).baseCatchWindowTicks() == expectedTicks[i];
        }
    }

    private static void checkCatchGates() {
        FishRarity[] rarities = FishRarity.values();
        for (double luck : new double[]{0.0, 5.5, 11.0}) {
            double[] full = FishRarity.weightsAt(luck, 500);
            double fullTotal = 0.0;
            for (double weight : full) fullTotal += weight;

            for (int catches : new int[]{0, 99, 100, 299, 300, 499, 500}) {
                double[] weights = FishRarity.weightsAt(luck, catches);
                double total = 0.0;
                for (double weight : weights) total += weight;
                assert Math.abs(total - fullTotal) < 0.000001;
                assert Math.abs(weights[FishRarity.COMMON.ordinal()] - full[FishRarity.COMMON.ordinal()]) < 0.000001;

                double unlockedScale = -1.0;
                for (int index = 1; index < rarities.length; index++) {
                    if (catches < rarities[index].requiredCatches()) {
                        assert weights[index] == 0.0;
                    } else {
                        double scale = weights[index] / full[index];
                        if (unlockedScale < 0.0) unlockedScale = scale;
                        assert Math.abs(scale - unlockedScale) < 0.000001;
                    }
                }
            }
        }
    }

    private static void checkWeightedFishSelection() {
        FishingPersonality common = personality(3.0F, 8.0F);
        FishingCatches.CatchEntry unrestricted = new FishingCatches.CatchEntry(
                new ItemStack(Items.COD), common, null, 1);
        FishingCatches.CatchEntry oneCondition = new FishingCatches.CatchEntry(
                new ItemStack(Items.SALMON), common, null, 2);
        FishingCatches.CatchEntry twoConditions = new FishingCatches.CatchEntry(
                new ItemStack(Items.TROPICAL_FISH), common, null, 3);
        List<FishingCatches.CatchEntry> entries = List.of(unrestricted, oneCondition, twoConditions);
        RandomSource random = RandomSource.create(219L);
        int[] counts = new int[3];
        for (int i = 0; i < 60_000; i++) {
            FishingCatches.CatchEntry selected = FishingCatches.selectEntry(entries, random);
            counts[entries.indexOf(selected)]++;
        }
        assert counts[0] >= 9_500 && counts[0] <= 10_500;
        assert counts[1] >= 19_000 && counts[1] <= 21_000;
        assert counts[2] >= 28_500 && counts[2] <= 31_500;
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
        return personality(averageBounces, averageCatchSeconds, FishRarity.COMMON);
    }

    private static FishingPersonality personality(
            float averageBounces,
            float averageCatchSeconds,
            FishRarity rarity
    ) {
        return new FishingPersonality(
                rarity,
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
