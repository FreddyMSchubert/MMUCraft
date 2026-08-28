package uk.co.httpsmmuminecraftsociety.mainmod.toggles;

import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;

import java.util.HashMap;
import java.util.Map;

public final class FeatureToggles {
    public static final String NETHER = "nether";
    public static final String END = "end";

    private static volatile Map<String, Boolean> values = Map.of(NETHER, false, END, false);

    private FeatureToggles() {}

    public static boolean isEnabled(String key) {
        return values.getOrDefault(key, false);
    }

    public static void apply(FeatureTogglesSnapshot snapshot) {
        Map<String, Boolean> updated = new HashMap<>();
        snapshot.getTogglesList().forEach(toggle -> updated.put(toggle.getKey(), toggle.getEnabled()));
        values = Map.copyOf(updated);
    }
}
