package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.util.Mth;

public record FishingPersonality(
        FishRarity rarity,
        float struggleSeconds,
        String fishShape,
        float size,
        float secondsAwayFromBobber,
        float approachSeconds,
        float retreatSeconds,
        float retreatDistance,
        float averageBounces
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

    public int retreatTicks() {
        return Mth.ceil(retreatSeconds * 20.0F);
    }
}
