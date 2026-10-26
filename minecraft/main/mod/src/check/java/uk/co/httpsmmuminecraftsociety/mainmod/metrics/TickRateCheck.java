package uk.co.httpsmmuminecraftsociety.mainmod.metrics;

public final class TickRateCheck {
    public static void main(String[] args) {
        assert MetricsServer.ticksPerSecond(20, 1_000_000_000L) == 20.0;
        assert MetricsServer.ticksPerSecond(20, 2_000_000_000L) == 10.0;
        System.out.println("Tick rate checks passed.");
    }
}
