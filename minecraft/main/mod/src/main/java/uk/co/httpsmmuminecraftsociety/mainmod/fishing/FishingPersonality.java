package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.Mth;

public record FishingPersonality(
        FishRarity rarity,
        float struggleSeconds,
		float textureAngleDegrees,
		boolean randomTextureAngle,
		float textureLengthPixels,
        float size,
        float secondsAwayFromBobber,
        float approachSeconds,
        float retreatSeconds,
        float retreatDistance,
        float averageBounces,
        float averageCatchSeconds
) {
    public int struggleTicks() {
        return Mth.ceil(struggleSeconds * 20.0F);
    }

    public int baseCatchWindowTicks() {
        float seconds = 0.8F - (rarity.catchLevel() - 1) * 0.125F;
        return Math.max(1, Mth.ceil(seconds * 20.0F));
    }

    public int awayTicks() {
        return Mth.ceil(secondsAwayFromBobber * 20.0F);
    }

    public int approachTicks() {
        return Mth.ceil(approachSeconds * 20.0F);
    }

    public int approachTicks(double distance) {
        return Math.max(1, Mth.ceil(distance / retreatDistance * approachTicks()));
    }

    public int retreatTicks() {
        return Mth.ceil(retreatSeconds * 20.0F);
    }

    public FishingPersonality withSize(float newSize) {
        return new FishingPersonality(
                rarity,
                struggleSeconds,
				textureAngleDegrees,
				randomTextureAngle,
				textureLengthPixels,
                newSize,
                secondsAwayFromBobber,
                approachSeconds,
                retreatSeconds,
                retreatDistance,
                averageBounces,
                averageCatchSeconds
        );
    }

	public FishingPersonality resolveTextureAngle(net.minecraft.util.RandomSource random) {
		if (!randomTextureAngle) return this;
		return new FishingPersonality(
				rarity,
				struggleSeconds,
				random.nextFloat() * 360.0F,
				false,
				textureLengthPixels,
				size,
				secondsAwayFromBobber,
				approachSeconds,
				retreatSeconds,
				retreatDistance,
				averageBounces,
				averageCatchSeconds
		);
	}
}
