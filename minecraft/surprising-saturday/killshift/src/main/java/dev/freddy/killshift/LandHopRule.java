package dev.freddy.killshift;

public final class LandHopRule {
    private LandHopRule() {}

    public static boolean exceedsLimit(double dx, double dy, double dz,
            boolean serverOnGround, boolean packetOnGround, double airSpeed) {
        boolean launching = !packetOnGround && dy > 0.1;
        double limit = serverOnGround && !launching ? 0.001 : airSpeed;
        return Math.hypot(dx, dz) > limit;
    }
}
