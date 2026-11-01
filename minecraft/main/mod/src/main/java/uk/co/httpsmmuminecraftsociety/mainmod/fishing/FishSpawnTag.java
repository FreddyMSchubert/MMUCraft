package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum FishSpawnTag {
    WARM("warm", Group.CLIMATE, "hot biomes"),
    COLD("cold", Group.CLIMATE, "cold biomes"),
    NETHER_WASTES("nether_wastes", Group.NETHER_BIOME, "the Nether Wastes"),
    SOUL_SAND_VALLEY("soul_sand_valley", Group.NETHER_BIOME, "Soul Sand Valleys"),
    CRIMSON_FOREST("crimson_forest", Group.NETHER_BIOME, "Crimson Forests"),
    WARPED_FOREST("warped_forest", Group.NETHER_BIOME, "Warped Forests"),
    BASALT_DELTAS("basalt_deltas", Group.NETHER_BIOME, "the Basalt Deltas"),
    SMALL_END_ISLANDS("small_end_islands", Group.END_BIOME, "Small End Islands"),
    END_MIDLANDS("end_midlands", Group.END_BIOME, "End Midlands"),
    END_HIGHLANDS("end_highlands", Group.END_BIOME, "End Highlands"),
    END_BARRENS("end_barrens", Group.END_BIOME, "End Barrens"),
    RIVER("river", Group.WATER, "rivers"),
    OCEAN("ocean", Group.WATER, "oceans"),
    DAY("day", Group.TIME, "daytime"),
    NIGHT("night", Group.TIME, "nighttime"),
    DEEP("deep", Group.HEIGHT, "below Y 60"),
    HIGH("high", Group.HEIGHT, "above Y 100"),
    DENSE_VOID("dense_void", Group.END_HEIGHT, "the dense void (Y ≤ 5)"),
    EQUIVOID("equivoid", Group.END_HEIGHT, "the equivoid (Y 6–244)"),
    SPARSE_VOID("sparse_void", Group.END_HEIGHT, "the sparse void (Y ≥ 245)"),
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

    public static boolean hasNetherBiome(Set<FishSpawnTag> tags) {
        return tags.stream().anyMatch(tag -> tag.group == Group.NETHER_BIOME);
    }

    public static String conditionSentence(Set<FishSpawnTag> tags) {
        List<String> clauses = new ArrayList<>();
        for (Group group : Group.values()) {
            List<String> alternatives = new ArrayList<>();
            String prefix = switch (group) {
                case CLIMATE, NETHER_BIOME, END_BIOME, WATER, HEIGHT, END_HEIGHT -> "in ";
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
        } else if (level.dimension() == Level.NETHER) {
            if (biome.is(Biomes.NETHER_WASTES)) active.add(NETHER_WASTES);
            if (biome.is(Biomes.SOUL_SAND_VALLEY)) active.add(SOUL_SAND_VALLEY);
            if (biome.is(Biomes.CRIMSON_FOREST)) active.add(CRIMSON_FOREST);
            if (biome.is(Biomes.WARPED_FOREST)) active.add(WARPED_FOREST);
            if (biome.is(Biomes.BASALT_DELTAS)) active.add(BASALT_DELTAS);
        } else if (level.dimension() == Level.END) {
            if (biome.is(Biomes.SMALL_END_ISLANDS)) active.add(SMALL_END_ISLANDS);
            if (biome.is(Biomes.END_MIDLANDS)) active.add(END_MIDLANDS);
            if (biome.is(Biomes.END_HIGHLANDS)) active.add(END_HIGHLANDS);
            if (biome.is(Biomes.END_BARRENS)) active.add(END_BARRENS);
            if (pos.getY() <= level.getMinY() + 5) active.add(DENSE_VOID);
            else if (pos.getY() >= level.getMaxY() - 10) active.add(SPARSE_VOID);
            else active.add(EQUIVOID);
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
        CLIMATE, NETHER_BIOME, END_HEIGHT, END_BIOME, WATER, TIME, HEIGHT, WEATHER, MOON
    }
}
