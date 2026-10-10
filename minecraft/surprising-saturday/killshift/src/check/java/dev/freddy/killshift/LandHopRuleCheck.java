package dev.freddy.killshift;

public final class LandHopRuleCheck {
    public static void main(String[] args) {
        assert LandHopRule.exceedsLimit(0.02, 0.0, 0.0, true, true, 0.06);
        assert !LandHopRule.exceedsLimit(0.02, 0.4, 0.0, true, false, 0.06);
        assert LandHopRule.exceedsLimit(0.08, 0.1, 0.0, false, false, 0.06);
        assert !LandHopRule.exceedsLimit(0.04, -0.1, 0.0, false, false, 0.06);
        assert LandHopRule.exceedsLimit(0.02, -0.1, 0.0, true, false, 0.06);
        System.out.println("Land hop checks passed");
    }
}
