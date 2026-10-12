package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public final class FishRarityCheck {
    private FishRarityCheck() {}

    public static void main(String[] args) {
        double[] zero = FishRarity.weightsAt(0, 500);
        double[] max = FishRarity.weightsAt(11, 500);
        assert close(zero[FishRarity.COMMON.ordinal()], 0.7813619053029156);
        assert close(max[FishRarity.COMMON.ordinal()], 0.7300715950715951);
        assert close(zero[FishRarity.LEGENDARY.ordinal()], 0.00040227526892101725);
        assert close(max[FishRarity.LEGENDARY.ordinal()], 0.016346153846153846);
        assert close(zero[FishRarity.MYTHICAL.ordinal()], 0.0000348638566398215);
        assert close(max[FishRarity.MYTHICAL.ordinal()], 0.0025);

        for (double luck : new double[]{-1, 0, 1, 2, 3, 4, 5, 5.5, 6, 7, 8, 9, 10, 11, 12}) {
            double progress = Math.max(0, Math.min(11, luck)) / 11;
            double[] full = FishRarity.weightsAt(luck, 500);
            assert close(sum(full), 1.0);
            for (FishRarity rarity : FishRarity.values()) {
                int index = rarity.ordinal();
                assert close(full[index], zero[index] + (max[index] - zero[index]) * progress);
            }

            for (int catches : new int[]{0, 99, 100, 299, 300, 499, 500}) {
                double[] gated = FishRarity.weightsAt(luck, catches);
                assert close(sum(gated), 1.0);
                for (FishRarity rarity : FishRarity.values()) {
                    if (rarity == FishRarity.COMMON) continue;
                    assert close(gated[rarity.ordinal()], catches < rarity.requiredCatches() ? 0.0 : full[rarity.ordinal()]);
                }
                assert gated[FishRarity.COMMON.ordinal()] >= full[FishRarity.COMMON.ordinal()];
            }
        }
    }

    private static double sum(double[] weights) {
        double total = 0.0;
        for (double weight : weights) total += weight;
        return total;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-10;
    }
}
