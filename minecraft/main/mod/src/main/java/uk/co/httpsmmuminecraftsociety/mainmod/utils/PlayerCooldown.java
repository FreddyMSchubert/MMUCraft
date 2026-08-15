package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerCooldown {
    private final long durationNanos;
    private final Map<UUID, Long> readyAtNanos = new HashMap<>();

    public PlayerCooldown(Duration duration) {
        durationNanos = duration.toNanos();
        if (durationNanos <= 0) throw new IllegalArgumentException("Cooldown duration must be positive");
    }

    public synchronized boolean isReady(UUID playerId) {
        return System.nanoTime() >= readyAtNanos.getOrDefault(playerId, 0L);
    }

    public synchronized boolean tryStart(UUID playerId) {
        long now = System.nanoTime();
        if (now < readyAtNanos.getOrDefault(playerId, 0L)) return false;
        readyAtNanos.put(playerId, now + durationNanos);
        return true;
    }
}
