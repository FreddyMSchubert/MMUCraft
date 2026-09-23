package dev.freddy.killshift;

record MobTraits(
        boolean aquatic,
        boolean waterBreathing,
        boolean flying,
        boolean forcedFlight,
        boolean undead,
        boolean wallClimber,
        boolean bouncy,
        boolean lavaSafe,
        boolean noFallDamage,
        double speedMultiplier,
        double jumpMultiplier,
        float flightSpeed
) {
    static final MobTraits DEFAULT = new MobTraits(
            false, false, false, false, false, false, false, false, false,
            1.0, 1.0, 0.05F
    );
}
