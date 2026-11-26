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
                for (int mask = 0; mask < 64; mask++) {
                    double[] boosted = FishRarity.weightsWithBoost(luck, catches, mask);
                    assert close(sum(boosted), 1.0);
                    for (double chance : boosted) assert chance >= 0.0;
                    for (FishRarity target : FishRarity.values()) {
                        int bit = 1 << target.ordinal();
                        if ((mask & bit) != 0) {
                            double[] without = FishRarity.weightsWithBoost(luck, catches, mask & ~bit);
                            assert boosted[target.ordinal()] > without[target.ordinal()];
                        }
                    }
                }
            }
        }

        int upperMask = 1 << FishRarity.LEGENDARY.ordinal() | 1 << FishRarity.MYTHICAL.ordinal();
        double[] early = FishRarity.weightsWithBoost(0, 0, upperMask);
        assert close(early[FishRarity.LEGENDARY.ordinal()], 0.009881422924901186);
        assert close(early[FishRarity.MYTHICAL.ordinal()], 0.001976284584980237);

        double[] lucky = FishRarity.weightsWithBoost(11, 0, upperMask);
        assert close(lucky[FishRarity.LEGENDARY.ordinal()], 0.03256530180511161);
        assert close(lucky[FishRarity.MYTHICAL.ordinal()], 0.00500463632174037);

        double[] all = FishRarity.weightsWithBoost(11, 500, 63);
        assert close(sum(all), 1.0);

        for (FishRarity rarity : FishRarity.values()) {
            int index = rarity.ordinal();
            double[] without = FishRarity.weightsWithBoost(11, 500, 63 & ~(1 << index));
            assert all[index] > without[index];
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
