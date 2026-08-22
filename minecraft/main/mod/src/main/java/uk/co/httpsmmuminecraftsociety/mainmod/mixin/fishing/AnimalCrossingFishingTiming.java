package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

final class AnimalCrossingFishingTiming {
    private static final int MAX_LATENCY_COMPENSATION_TICKS = 20;
    private static final int WAIT_CENTER_TICKS_WITHOUT_LURE = 20 * 30;
    private static final int WAIT_CENTER_TICKS_WITH_LURE_3 = 20 * 5;
    private static final int WAIT_SPREAD_TICKS_WITHOUT_LURE = 20 * 5;
    private static final int WAIT_SPREAD_TICKS_WITH_LURE_3 = 20 * 3;
    private static final double BOUNCE_GAUSSIAN_SIGMA = 1.0D;
    private static final double BASE_FISH_DISPLAY_WIDTH_BLOCKS = 1.22D;
    private static final double BOBBER_TOUCH_PADDING_BLOCKS = 0.10D;

	private AnimalCrossingFishingTiming() {}

	record DistanceMovement(double distance, int remainingTicks, boolean reachedTarget) {}

	static DistanceMovement moveToward(double distance, double targetDistance, int remainingTicks) {
		if (remainingTicks <= 0) return new DistanceMovement(targetDistance, 0, true);

		double nextDistance = distance + (targetDistance - distance) / remainingTicks;
		int nextRemainingTicks = remainingTicks - 1;
		return nextRemainingTicks <= 0
				? new DistanceMovement(targetDistance, 0, true)
				: new DistanceMovement(nextDistance, nextRemainingTicks, false);
	}

    static double bobberContactDistance(FishingPersonality personality) {
        return BASE_FISH_DISPLAY_WIDTH_BLOCKS * personality.size() * 0.5D
                + BOBBER_TOUCH_PADDING_BLOCKS;
    }

    static int biteWindowTicks(FishingHook hook, FishingPersonality personality) {
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            int latencyCompensationTicks = Mth.ceil(player.connection.latency() / 50.0F);
            return personality.baseCatchWindowTicks()
                    + Math.min(MAX_LATENCY_COMPENSATION_TICKS, latencyCompensationTicks);
        }
        return personality.baseCatchWindowTicks();
    }

    static int initialWaitTicks(RandomSource random, int lureSpeed) {
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
        return Mth.nextInt(random, Math.max(20, centerTicks - spreadTicks), centerTicks + spreadTicks);
    }

    static int lureLevel(int lureSpeed) {
        if (lureSpeed <= 3) return Mth.clamp(lureSpeed, 0, 3);
        return Mth.clamp(Math.round(lureSpeed / 100.0F), 0, 3);
    }

    static int rollBounceCount(RandomSource random, FishingPersonality personality) {
        double averageBounces = personality.averageBounces();
        int rightEdge = Math.max(1, Mth.ceil(averageBounces * 2.0D - 1.0D));
        if (rightEdge <= 1) return 1;

        double totalWeight = 0.0D;
        for (int bounces = 1; bounces <= rightEdge; bounces++) {
            totalWeight += bounceWeight(bounces, averageBounces);
        }

        double roll = random.nextDouble() * totalWeight;
        for (int bounces = 1; bounces <= rightEdge; bounces++) {
            roll -= bounceWeight(bounces, averageBounces);
            if (roll <= 0.0D) return bounces;
        }
        return rightEdge;
    }

    private static int lureScaledTicks(int noLureTicks, int lureThreeTicks, int lureLevel) {
        double lureProgress = lureLevel / 3.0D;
        return Mth.ceil(noLureTicks + (lureThreeTicks - noLureTicks) * lureProgress);
    }

    private static double bounceWeight(int bounces, double averageBounces) {
        double offset = (bounces - averageBounces) / BOUNCE_GAUSSIAN_SIGMA;
        return Math.exp(-0.5D * offset * offset);
    }
}
