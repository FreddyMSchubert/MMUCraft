package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;

public final class AnimalCrossingFishingTiming {
    private static final int MAX_LATENCY_COMPENSATION_TICKS = 20;
    private static final int WAIT_CENTER_TICKS_WITHOUT_LURE = 20 * 24;
    private static final int WAIT_CENTER_TICKS_WITH_LURE_3 = 20 * 5;
    private static final int WAIT_SPREAD_TICKS_WITHOUT_LURE = 20 * 4;
    private static final int WAIT_SPREAD_TICKS_WITH_LURE_3 = 20 * 3;
    private static final int LURE_3_EXTRA_RANDOM_WAIT_TICKS = 20 * 2;
    private static final double BOBBER_TOUCH_PADDING_BLOCKS = 0.10D;
    // One shared bell curve; species tendency tilts it earlier or later.
    private static final double[] BITE_WEIGHTS = {
            0.473723D, 0.737029D, 0.901254D, 1.0D, 0.877033D,
            0.689601D, 0.413890D, 0.115015D, 0.028741D, 0.003716D
    };

	private AnimalCrossingFishingTiming() {}

	public record DistanceMovement(double distance, int remainingTicks, boolean reachedTarget) {}

	public static DistanceMovement moveToward(double distance, double targetDistance, int remainingTicks) {
		if (remainingTicks <= 0) return new DistanceMovement(targetDistance, 0, true);

		double nextDistance = distance + (targetDistance - distance) / remainingTicks;
		int nextRemainingTicks = remainingTicks - 1;
		return nextRemainingTicks <= 0
				? new DistanceMovement(targetDistance, 0, true)
				: new DistanceMovement(nextDistance, nextRemainingTicks, false);
	}

    public static double bobberContactDistance(FishingPersonality personality) {
        return personality.size() * 0.5D
                + BOBBER_TOUCH_PADDING_BLOCKS;
    }

    public static int biteWindowTicks(FishingHook hook, FishingPersonality personality) {
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            int latencyCompensationTicks = Mth.ceil(player.connection.latency() / 50.0F);
            return personality.baseCatchWindowTicks()
                    + Math.min(MAX_LATENCY_COMPENSATION_TICKS, latencyCompensationTicks);
        }
        return personality.baseCatchWindowTicks();
    }

    public static int initialWaitTicks(RandomSource random, int lureSpeed) {
        int lureLevel = lureLevel(lureSpeed);
        int centerTicks = lureScaledTicks(
                WAIT_CENTER_TICKS_WITHOUT_LURE,
                WAIT_CENTER_TICKS_WITH_LURE_3,
                lureLevel
        );
        int spreadTicks = lureScaledTicks(
                WAIT_SPREAD_TICKS_WITHOUT_LURE,
                WAIT_SPREAD_TICKS_WITH_LURE_3,
                lureLevel
        );
        int waitTicks = Mth.nextInt(random, Math.max(20, centerTicks - spreadTicks), centerTicks + spreadTicks);
        return lureLevel == 3
                ? waitTicks + Mth.nextInt(random, 0, LURE_3_EXTRA_RANDOM_WAIT_TICKS)
                : waitTicks;
    }

    public static int lureLevel(int lureSpeed) {
        if (lureSpeed <= 3) return Mth.clamp(lureSpeed, 0, 3);
        return Mth.clamp(Math.round(lureSpeed / 100.0F), 0, 3);
    }

    public static double lureBookShortcutChance(int lureLevel) {
        return switch (Mth.clamp(lureLevel, 0, 3)) {
            case 0 -> 0.25D;
            case 1 -> 0.125D;
            case 2 -> 0.05D;
            default -> 0.0D;
        };
    }

    public static boolean shouldBite(RandomSource random, FishingPersonality personality, int contact) {
        return random.nextDouble() < biteChance(personality, contact);
    }

    static double biteChance(FishingPersonality personality, int contact) {
        if (contact >= BITE_WEIGHTS.length) return 1.0D;

        float tendency = personality.bounceTendency();
        double bias = (Math.min(tendency, 10.0F) - 5.625D) / 20.0D
                + Math.max(0.0F, tendency - 10.0F) * 0.2D;
        double current = BITE_WEIGHTS[contact - 1] * Math.exp(bias * (contact - 4));
        double remaining = current;
        for (int next = contact + 1; next <= BITE_WEIGHTS.length; next++) {
            remaining += BITE_WEIGHTS[next - 1] * Math.exp(bias * (next - 4));
        }
        return current / remaining;
    }

    private static int lureScaledTicks(int noLureTicks, int lureThreeTicks, int lureLevel) {
        double lureProgress = lureLevel / 3.0D;
        return Mth.ceil(noLureTicks + (lureThreeTicks - noLureTicks) * lureProgress);
    }

}
