package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;

import java.util.EnumSet;
import java.util.Set;

public enum FishSpawnTag {
    WARM("warm", Group.CLIMATE),
    COLD("cold", Group.CLIMATE),
    TEMPERATE("temperate", Group.CLIMATE),
    RIVER("river", Group.WATER),
    OCEAN("ocean", Group.WATER),
    DAY("day", Group.TIME),
    NIGHT("night", Group.TIME),
    DEEP("deep", Group.HEIGHT),
    HIGH("high", Group.HEIGHT),
    SUNNY("sunny", Group.WEATHER),
    RAINY("rainy", Group.WEATHER),
    THUNDERSTORM("thunderstorm", Group.WEATHER),
    SNOWY("snowy", Group.WEATHER),
    WAXING("waxing", Group.MOON),
    WANING("waning", Group.MOON),
    FULLMOON("fullmoon", Group.MOON),
    NEWMOON("newmoon", Group.MOON);

    private final String jsonValue;
    private final Group group;

    FishSpawnTag(String jsonValue, Group group) {
        this.jsonValue = jsonValue;
        this.group = group;
    }

    public static FishSpawnTag fromJsonValue(String value) {
        for (FishSpawnTag tag : values()) {
            if (tag.jsonValue.equals(value)) {
                return tag;
            }
        }
        throw new IllegalArgumentException("Unknown fish spawn tag: " + value);
    }

    public static boolean matches(Set<FishSpawnTag> required, Level level, BlockPos pos) {
        if (required.isEmpty()) {
            return true;
        }

        EnumSet<FishSpawnTag> active = EnumSet.noneOf(FishSpawnTag.class);
        Holder<Biome> biome = level.getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        if (level.dimension() == Level.NETHER) {
            active.add(WARM);
        } else if (level.dimension() == Level.END) {
            active.add(COLD);
        } else if (temperature >= 0.95F) {
            active.add(WARM);
        } else if (temperature < 0.15F) {
            active.add(COLD);
        } else {
            active.add(TEMPERATE);
        }

        if (biome.is(BiomeTags.IS_RIVER)) active.add(RIVER);
        if (biome.is(BiomeTags.IS_OCEAN)) active.add(OCEAN);
        if (pos.getY() < 64) active.add(DEEP);
        if (pos.getY() > 100) active.add(HIGH);

        long clock = level.getOverworldClockTime();
        active.add(Math.floorMod(clock, 24_000L) < 13_000L ? DAY : NIGHT);
        addMoonTags(active, MoonPhase.values()[(int) Math.floorMod(Math.floorDiv(clock, 24_000L), MoonPhase.COUNT)]);

        Biome.Precipitation precipitation = level.precipitationAt(pos);
        if (!level.isRaining() || precipitation == Biome.Precipitation.NONE) {
            active.add(SUNNY);
        } else if (precipitation == Biome.Precipitation.SNOW) {
            active.add(SNOWY);
        } else {
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
