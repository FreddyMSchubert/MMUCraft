package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails;

import net.minecraft.util.RandomSource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class WeightedTrailSpec {
    public static final WeightedTrailSpec EMPTY = new WeightedTrailSpec(Map.of());

    private final Map<TrailParticle, Integer> weights;
    private final long totalWeight;
    private final long dustWeight;
    private final int interval;

    public WeightedTrailSpec(Map<TrailParticle, Integer> weights) {
        Map<TrailParticle, Integer> valid = new EnumMap<>(TrailParticle.class);
        weights.forEach((particle, weight) -> addWeight(valid, particle, weight));
        this.weights = Collections.unmodifiableMap(valid);
        this.totalWeight = valid.values().stream().mapToLong(Integer::longValue).sum();
        this.dustWeight = valid.entrySet().stream().filter(entry -> entry.getKey().isBasicDust())
                .mapToLong(Map.Entry::getValue).sum();
        this.interval = valid.keySet().stream().mapToInt(particle -> particle.interval).max().orElse(1);
    }

    public Map<TrailParticle, Integer> weights() {
        return weights;
    }

    public long totalWeight(boolean member) {
        return member ? totalWeight : dustWeight;
    }

    public int interval(boolean member) {
        return member ? interval : 1;
    }

    public TrailParticle pick(RandomSource random, boolean member) {
        long total = totalWeight(member);
        if (total == 0) return null;
        long choice = (long) (random.nextDouble() * total);
        for (var entry : weights.entrySet()) {
            if (!member && !entry.getKey().isBasicDust()) continue;
            choice -= entry.getValue();
            if (choice < 0) return entry.getKey();
        }
        return null;
    }

    public static void addWeight(Map<TrailParticle, Integer> weights, TrailParticle particle, int weight) {
        if (particle == null || weight <= 0) return;
        weights.merge(particle, weight, (existing, added) -> (int) Math.min(Integer.MAX_VALUE, (long) existing + added));
    }
}
