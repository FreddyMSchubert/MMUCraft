package uk.co.mmuminecraftsociety.velocity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class CapacityPolicy {
    private CapacityPolicy() { }

    record Candidate(UUID uuid, int priority, long joinedAt, boolean exempt) { }

    static boolean canAdmit(int occupied, int maximum, int priority, List<Candidate> connected) {
        int needed = occupied + 1 - maximum;
        return needed <= 0 || connected.stream()
                .filter(candidate -> !candidate.exempt() && candidate.priority() < priority)
                .count() >= needed;
    }

    static List<UUID> victims(List<Candidate> connected, int excess) {
        return connected.stream()
                .filter(candidate -> !candidate.exempt())
                .sorted(Comparator.comparingInt(Candidate::priority)
                        .thenComparingLong(Candidate::joinedAt)
                        .thenComparing(candidate -> candidate.uuid().toString()))
                .limit(excess)
                .map(Candidate::uuid)
                .toList();
    }
}
