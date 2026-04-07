package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class WeightedTrailSpec {
    public static final WeightedTrailSpec EMPTY = new WeightedTrailSpec(List.of(), 0);

    public record Entry(DyeColor dye, int weight) {}

    private final List<Entry> entries;
    private final int totalWeight;

    public WeightedTrailSpec(List<Entry> entries, int totalWeight) {
        this.entries = Collections.unmodifiableList(entries);
        this.totalWeight = totalWeight;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty() || this.totalWeight <= 0;
    }

    public int totalWeight() {
        return this.totalWeight;
    }

    public DyeColor pick(RandomSource random) {
        if (this.isEmpty()) {
            return DyeColor.WHITE;
        }

        int choice = random.nextInt(this.totalWeight);
        int running = 0;

        for (Entry entry : this.entries) {
            running += entry.weight();
            if (choice < running) {
                return entry.dye();
            }
        }

        return this.entries.get(this.entries.size() - 1).dye();
    }

    public String serialize() {
        if (this.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);

            if (i > 0) {
                builder.append(';');
            }

            builder.append(dyeName(entry.dye()))
                    .append('*')
                    .append(entry.weight());
        }

        return builder.toString();
    }

    public static WeightedTrailSpec deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        List<Entry> entries = new ArrayList<>();
        int totalWeight = 0;

        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] split = trimmed.split("\\*", 2);
            if (split.length != 2) {
                continue;
            }

            DyeColor dye = parseDye(split[0]);
            if (dye == null) {
                continue;
            }

            int weight;
            try {
                weight = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (weight <= 0) {
                continue;
            }

            entries.add(new Entry(dye, weight));
            totalWeight += weight;
        }

        return entries.isEmpty() ? EMPTY : new WeightedTrailSpec(entries, totalWeight);
    }

    public static String dyeName(DyeColor color) {
        return color.getName();
    }

    public static DyeColor parseDye(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);

        return switch (key) {
            case "white" -> DyeColor.WHITE;
            case "orange" -> DyeColor.ORANGE;
            case "magenta" -> DyeColor.MAGENTA;
            case "light_blue" -> DyeColor.LIGHT_BLUE;
            case "yellow" -> DyeColor.YELLOW;
            case "lime" -> DyeColor.LIME;
            case "pink" -> DyeColor.PINK;
            case "gray" -> DyeColor.GRAY;
            case "light_gray" -> DyeColor.LIGHT_GRAY;
            case "cyan" -> DyeColor.CYAN;
            case "purple" -> DyeColor.PURPLE;
            case "blue" -> DyeColor.BLUE;
            case "brown" -> DyeColor.BROWN;
            case "green" -> DyeColor.GREEN;
            case "red" -> DyeColor.RED;
            case "black" -> DyeColor.BLACK;
            default -> null;
        };
    }
}
