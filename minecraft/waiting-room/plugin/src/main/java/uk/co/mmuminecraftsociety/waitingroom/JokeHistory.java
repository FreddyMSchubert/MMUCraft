package uk.co.mmuminecraftsociety.waitingroom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

final class JokeHistory {
    private final Deque<String> recent = new ArrayDeque<>();
    private final Set<String> pending = new HashSet<>();

    boolean tryStart(String station) {
        if (recent.contains(station) || !pending.add(station)) return false;
        recent.addLast(station);
        if (recent.size() > 5) recent.removeFirst();
        return true;
    }

    void finish(String station, boolean success) {
        pending.remove(station);
        if (!success) recent.remove(station);
    }

    public static void main(String[] args) {
        JokeHistory history = new JokeHistory();
        assert history.tryStart("a");
        assert !history.tryStart("a");
        history.finish("a", false);
        assert history.tryStart("a");
        history.finish("a", true);
        assert !history.tryStart("a");
        for (String station : new String[] { "b", "c", "d", "e", "f" }) {
            assert history.tryStart(station);
            history.finish(station, true);
        }
        assert history.tryStart("a");
        assert !history.tryStart("f");
    }
}
