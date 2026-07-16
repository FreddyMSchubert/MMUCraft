package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;

import java.util.Set;

public final class FishingCatchesWeightsTest {
    @Test
    void fishingRulesStayStable() {
        check(FishRarity.COMMON, 0.0, 65.0);
        check(FishRarity.MYTHICAL, 11.0, 0.75);
        check(FishRarity.RARE, 5.5, 8.5);
        check(FishRarity.COMMON, -10.0, 65.0);
        check(FishRarity.COMMON, 20.0, 55.0);
        checkSizeDistribution();
        checkFurnaceResultEnum();
        checkGroupedFishParsing();
        checkGeneratedFishDefaults();
        checkInitialApproachSpeed();
        checkFishSpawnTags();
        if (FishRarity.UNCOMMON.colorRgb() != 0xFFFF55
                || FishRarity.RARE.colorRgb() != 0x55FFFF
                || FishRarity.EPIC.colorRgb() != 0xFF55FF
                || FishRarity.LEGENDARY.colorRgb() != 0x55FF55
                || FishRarity.MYTHICAL.colorRgb() != 0xFF5F00) {
            throw new AssertionError("Fish rarity colors must stay aligned with Minecraft's native rarity colors");
        }
    }

    private static void checkInitialApproachSpeed() {
        FishingPersonality personality = new FishingPersonality(
                FishRarity.COMMON, 1.0F, "shape", 1.0F,
                0.5F, 0.5F, 0.5F, 1.0F, 3.0F
        );
        if (personality.initialApproachTicks(5.0D) != personality.approachTicks() * 5) {
            throw new AssertionError("Initial approaches must use the configured bounce approach speed");
        }
    }

    private static void check(FishRarity rarity, double luck, double expected) {
        double actual = rarity.weightAtLuck(luck);
        if (Math.abs(actual - expected) > 0.000_001) {
            throw new AssertionError(rarity + " at luck " + luck + ": expected " + expected + ", got " + actual);
        }
    }

    private static void checkSizeDistribution() {
        FishSize size = new FishSize(50.0, 20.0);
        RandomSource random = RandomSource.create(42L);
        int outsideConfiguredDeviation = 0;
        int samples = 100_000;
        double total = 0.0;

        for (int i = 0; i < samples; i++) {
            double length = size.roll(random);
            total += length;
            if (Math.abs(length - size.averageCm()) > size.deviationCm()) {
                outsideConfiguredDeviation++;
            }
        }

        if (outsideConfiguredDeviation > samples * 0.012 || Math.abs(total / samples - size.averageCm()) > 0.1) {
            throw new AssertionError("Fish size roll no longer matches its configured bell curve");
        }
    }

    private static void checkFurnaceResultEnum() {
        if (FishFurnaceResult.fromJsonValue("white_meat") != FishFurnaceResult.WHITE_MEAT
                || FishFurnaceResult.fromJsonValue("red_meat") != FishFurnaceResult.RED_MEAT
                || FishFurnaceResult.fromJsonValue("crab_claw") != FishFurnaceResult.CRAB_CLAW
                || FishFurnaceResult.fromJsonValue("tentacle") != FishFurnaceResult.TENTACLE) {
            throw new AssertionError("Fish furnace-result JSON values are mapped incorrectly");
        }

        try {
            FishFurnaceResult.fromJsonValue("mystery_meat");
            throw new AssertionError("Unknown fish furnace results must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: the Java enum enforces the same values as the JSON schema.
        }
    }

    private static void checkGroupedFishParsing() {
        JsonObject root = new JsonObject();
        root.addProperty("id", "fish-test");
        root.addProperty("rarity", "rare");
        JsonObject fishJson = JsonParser.parseString("""
                {
                  "shape": "fish_shadow_default",
                  "tags": ["warm", "cold"],
                  "size": {"averageLengthCm": 50, "lengthDeviationCm": 20},
                  "catching": {
                    "shadowScale": 1.25,
                    "struggleSeconds": 2,
                    "secondsAwayFromBobber": 0.2,
                    "approachSeconds": 0.5,
                    "retreatSeconds": 1,
                    "retreatDistance": 0.75,
                    "averageCatchSeconds": 7
                  },
                  "furnaceResult": "red_meat"
                }
                """).getAsJsonObject();

        FishItemFeature feature = FishItemFeature.of(root, fishJson);
        if (feature.personality().size() != 1.25F
                || feature.length().averageCm() != 50.0
                || feature.furnaceResult() != FishFurnaceResult.RED_MEAT
                || !feature.spawnTags().equals(Set.of(FishSpawnTag.WARM, FishSpawnTag.COLD))) {
            throw new AssertionError("Grouped fish JSON was parsed incorrectly");
        }
    }

    private static void checkGeneratedFishDefaults() {
        JsonObject root = JsonParser.parseString("""
                {"id": "fish-stable-test", "rarity": "epic"}
                """).getAsJsonObject();
        JsonObject minimalFish = JsonParser.parseString("""
                {
                  "shape": "fish_shadow_default",
                  "tags": [],
                  "size": {"averageLengthCm": 60},
                  "furnaceResult": "white_meat"
                }
                """).getAsJsonObject();

        FishItemFeature first = FishItemFeature.of(root, minimalFish);
        FishItemFeature second = FishItemFeature.of(root, minimalFish);
        FishingPersonality personality = first.personality();
        float repeatSeconds = personality.retreatSeconds() + personality.secondsAwayFromBobber();
        float averageCatchSeconds = personality.averageBounces()
                * (personality.approachSeconds() + repeatSeconds) - repeatSeconds;
        if (!first.equals(second)
                || first.length().deviationCm() != 20.0
                || personality.size() < 0.5F || personality.size() >= 1.5F
                || personality.secondsAwayFromBobber() < 0.1F || personality.secondsAwayFromBobber() >= 0.5F
                || personality.approachSeconds() < 0.15F || personality.approachSeconds() >= 0.5F
                || personality.retreatSeconds() < 0.2F || personality.retreatSeconds() >= 1.5F
                || personality.retreatDistance() < 0.5F || personality.retreatDistance() >= 1.75F
                || averageCatchSeconds < 11.0F || averageCatchSeconds >= 19.0F
                || personality.struggleSeconds() < 2.5F || personality.struggleSeconds() >= 7.0F) {
            throw new AssertionError("Generated fish defaults must be stable and remain inside their configured ranges");
        }

        JsonObject overriddenFish = minimalFish.deepCopy();
        JsonObject catching = new JsonObject();
        catching.addProperty("shadowScale", 9.0F);
        overriddenFish.add("catching", catching);
        FishItemFeature overridden = FishItemFeature.of(root, overriddenFish);
        if (overridden.personality().size() != 9.0F
                || overridden.personality().secondsAwayFromBobber() != personality.secondsAwayFromBobber()) {
            throw new AssertionError("A catching override must not reroll unrelated defaults");
        }
    }

    private static void checkFishSpawnTags() {
        Set<FishSpawnTag> warmOrColdAtNight = Set.of(FishSpawnTag.WARM, FishSpawnTag.COLD, FishSpawnTag.NIGHT);
        if (!FishSpawnTag.matchesActive(warmOrColdAtNight, Set.of(FishSpawnTag.COLD, FishSpawnTag.NIGHT))
                || FishSpawnTag.matchesActive(warmOrColdAtNight, Set.of(FishSpawnTag.TEMPERATE, FishSpawnTag.NIGHT))
                || !FishSpawnTag.matchesActive(Set.of(), Set.of())) {
            throw new AssertionError("Fish tags must OR within a condition group and AND across groups");
        }

        if (FishSpawnTag.fromJsonValue("thunderstorm") != FishSpawnTag.THUNDERSTORM) {
            throw new AssertionError("Fish spawn tag JSON values are mapped incorrectly");
        }
        try {
            FishSpawnTag.fromJsonValue("stormish");
            throw new AssertionError("Unknown fish spawn tags must be rejected");
        } catch (IllegalArgumentException expected) {
            // The Java enum enforces the same values as the JSON schema.
        }
    }
}
