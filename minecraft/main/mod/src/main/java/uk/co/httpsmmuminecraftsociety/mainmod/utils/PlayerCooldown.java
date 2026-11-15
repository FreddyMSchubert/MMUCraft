package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerCooldown {
    private final long durationNanos;
    private final Map<UUID, Long> lastStartNanos = new HashMap<>();

    public PlayerCooldown(Duration duration) {
        durationNanos = duration.toNanos();
        if (durationNanos <= 0) throw new IllegalArgumentException("Cooldown duration must be positive");
    }

    public synchronized boolean isReady(UUID playerId) {
        return isReady(playerId, Duration.ofNanos(durationNanos));
    }

    public synchronized boolean isReady(UUID playerId, Duration duration) {
        Long last = lastStartNanos.get(playerId);
        return last == null || System.nanoTime() - last >= duration.toNanos();
    }

    public synchronized boolean tryStart(UUID playerId) {
        return tryStart(playerId, Duration.ofNanos(durationNanos));
    }

    public synchronized boolean tryStart(UUID playerId, Duration duration) {
        if (!isReady(playerId, duration)) return false;
        lastStartNanos.put(playerId, System.nanoTime());
        return true;
    }

    public synchronized void restart(UUID playerId) {
        lastStartNanos.put(playerId, System.nanoTime());
    }
}
