package uk.co.httpsmmuminecraftsociety.mainmod.toggles;

import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class FeatureToggles {
    public static final String NETHER = "nether";
    public static final String END = "end";
    public static final String WELCOMING = "welcoming";

    private static volatile Map<String, Boolean> values = Map.of();
    private static volatile long revision;
    private static final Map<String, List<Consumer<Boolean>>> listeners = new HashMap<>();

    private FeatureToggles() {}

    public static boolean isEnabled(String key) {
        return values.getOrDefault(key, false);
    }

    public static long revision() {
        return revision;
    }

    public static boolean isValidKey(String key) {
        return key.matches("[a-z0-9._-]+(?:/[a-z0-9._-]+)*");
    }

    public static void listen(String key, Consumer<Boolean> listener) {
        if (!isValidKey(key)) throw new IllegalArgumentException("invalid gameplay toggle id: " + key);
        listeners.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listener);
    }

    public static void apply(FeatureTogglesSnapshot snapshot) {
        Map<String, Boolean> updated = new HashMap<>();
        snapshot.getTogglesList().forEach(toggle -> updated.put(toggle.getKey(), toggle.getEnabled()));
        Map<String, Boolean> previous = values;
        values = Map.copyOf(updated);
        revision++;
        listeners.forEach((key, callbacks) -> {
            boolean enabled = isEnabled(key);
            if (previous.getOrDefault(key, false) != enabled) {
                callbacks.forEach(callback -> callback.accept(enabled));
            }
        });
    }
}
