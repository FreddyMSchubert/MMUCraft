package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

public record DailyTaskAmount(int baseCost, double perInstance) {
    public DailyTaskAmount {
        if (baseCost < 0 || !Double.isFinite(perInstance) || perInstance < 0.0D) {
            throw new IllegalArgumentException("Daily task amounts must not be negative");
        }
    }

    public int reward(int instances) {
        if (instances < 1) throw new IllegalArgumentException("Daily task instances must be positive");
        return baseCost + (int)Math.round(instances * perInstance);
    }
}
