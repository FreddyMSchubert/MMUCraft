package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum FishSpawnTag {
    WARM("warm", Group.CLIMATE, "hot biomes"),
    COLD("cold", Group.CLIMATE, "cold biomes"),
    RIVER("river", Group.WATER, "rivers"),
    OCEAN("ocean", Group.WATER, "oceans"),
    DAY("day", Group.TIME, "daytime"),
    NIGHT("night", Group.TIME, "nighttime"),
    DEEP("deep", Group.HEIGHT, "below Y 60"),
    HIGH("high", Group.HEIGHT, "above Y 100"),
    RAINY("rainy", Group.WEATHER, "rain"),
    THUNDERSTORM("thunderstorm", Group.WEATHER, "thunderstorms"),
    SNOWY("snowy", Group.WEATHER, "snowfall"),
    WAXING("waxing", Group.MOON, "waxing moon phases"),
    WANING("waning", Group.MOON, "waning moon phases"),
    FULLMOON("fullmoon", Group.MOON, "a full moon"),
    NEWMOON("newmoon", Group.MOON, "a new moon");

    private final String jsonValue;
    private final Group group;
    private final String phrase;

    FishSpawnTag(String jsonValue, Group group, String phrase) {
        this.jsonValue = jsonValue;
        this.group = group;
        this.phrase = phrase;
    }

    public static FishSpawnTag fromJsonValue(String value) {
        for (FishSpawnTag tag : values()) {
            if (tag.jsonValue.equals(value)) {
                return tag;
            }
        }
        throw new IllegalArgumentException("Unknown fish spawn tag: " + value);
    }

    public static String conditionSentence(Set<FishSpawnTag> tags) {
        List<String> clauses = new ArrayList<>();
        for (Group group : Group.values()) {
            List<String> alternatives = new ArrayList<>();
            String prefix = switch (group) {
                case CLIMATE, WATER, HEIGHT -> "in ";
                default -> "during ";
            };
            for (FishSpawnTag tag : values()) {
                if (tags.contains(tag) && tag.group == group) alternatives.add(prefix + tag.phrase);
            }
            if (alternatives.size() == 1) clauses.add(alternatives.getFirst());
            else if (alternatives.size() > 1) {
                clauses.add("(" + String.join(", ", alternatives.subList(0, alternatives.size() - 1))
                        + " or " + alternatives.getLast() + ")");
            }
        }
        return clauses.isEmpty() ? "anywhere and at any time" : String.join(" ", clauses);
    }

    public static boolean matches(Set<FishSpawnTag> required, Level level, BlockPos pos) {
        if (required.isEmpty()) {
            return true;
        }

        EnumSet<FishSpawnTag> active = EnumSet.noneOf(FishSpawnTag.class);
        Holder<Biome> biome = level.getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        // Climate tags deliberately describe Overworld biomes, never dimensions.
        if (level.dimension() == Level.OVERWORLD) {
            if (temperature >= 0.95F) active.add(WARM);
            if (temperature < 0.15F) active.add(COLD);
        }

        if (biome.is(BiomeTags.IS_RIVER)) active.add(RIVER);
        if (biome.is(BiomeTags.IS_OCEAN)) active.add(OCEAN);
        if (pos.getY() < 60) active.add(DEEP);
        if (pos.getY() > 100) active.add(HIGH);

        long clock = level.getOverworldClockTime();
        active.add(Math.floorMod(clock, 24_000L) < 13_000L ? DAY : NIGHT);
        addMoonTags(active, MoonPhase.values()[(int) Math.floorMod(Math.floorDiv(clock, 24_000L), MoonPhase.COUNT)]);

        // Check the air above the bobber for precipitation.
        Biome.Precipitation precipitation = level.precipitationAt(pos.above());
        if (level.isRaining() && precipitation == Biome.Precipitation.SNOW) {
            active.add(SNOWY);
        } else if (level.isRaining() && precipitation != Biome.Precipitation.NONE) {
            // Thunderstorms are rainy too; the specific tag merely narrows that condition.
            active.add(RAINY);
            if (level.isThundering()) active.add(THUNDERSTORM);
        }

        return matchesActive(required, active);
    }

    static boolean matchesActive(Set<FishSpawnTag> required, Set<FishSpawnTag> active) {
        for (Group group : Group.values()) {
            boolean mentionsGroup = required.stream().anyMatch(tag -> tag.group == group);
            boolean matchesGroup = required.stream().anyMatch(tag -> tag.group == group && active.contains(tag));
            if (mentionsGroup && !matchesGroup) {
                return false;
            }
        }
        return true;
    }

    private static void addMoonTags(EnumSet<FishSpawnTag> active, MoonPhase phase) {
        switch (phase) {
            case FULL_MOON -> active.add(FULLMOON);
            case NEW_MOON -> active.add(NEWMOON);
            case WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS -> active.add(WAXING);
            case WANING_GIBBOUS, THIRD_QUARTER, WANING_CRESCENT -> active.add(WANING);
        }
    }

    private enum Group {
        CLIMATE, WATER, TIME, HEIGHT, WEATHER, MOON
    }
}
