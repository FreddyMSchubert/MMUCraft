package dev.freddy.chunklocked;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** One shared set of unlocked chunks for every player. */
final class ChunkWorld {
    static final class Cell {
        String dimension;
        int x;
        int z;
        int y;
        String item;
        int remaining;
        boolean unlocked;
        String colour;

        Cell(String dimension, int x, int z, int y, String item, int remaining, boolean unlocked) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.y = y;
            this.item = item;
            this.remaining = remaining;
            this.unlocked = unlocked;
            this.colour = dimension.endsWith("the_nether") ? "yellow"
                    : dimension.endsWith("the_end") ? "purple" : "light_blue";
        }

        String tag() {
            return "cl_" + dimension.substring(dimension.indexOf(':') + 1) + "_" + x + "_" + z;
        }
    }

    static final class Origin {
        int x;
        int z;

        Origin(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final LootRoller loot = new LootRoller();
    private final Map<String, Cell> cells = new HashMap<>();
    private final Map<String, Origin> origins = new HashMap<>();
    private final Map<String, Integer> paid = new HashMap<>();

    private ChunkWorld(Path file) {
        this.file = file;
    }

    static ChunkWorld load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("chunklocked.json");
        if (Files.exists(file)) {
            try {
                State state = GSON.fromJson(Files.readString(file), State.class);
                ChunkWorld world = new ChunkWorld(file);
                world.cells.putAll(state.cells);
                world.origins.putAll(state.origins);
                world.paid.putAll(state.paid);
                return world;
            } catch (Exception error) {
                throw new IllegalStateException("Cannot read Chunklock world state " + file, error);
            }
        }
        return new ChunkWorld(file);
    }

    private record State(Map<String, Cell> cells, Map<String, Origin> origins, Map<String, Integer> paid) { }

    static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    static String key(String dimension, int x, int z) {
        return dimension + "/" + x + "/" + z;
    }

    Cell get(ServerLevel level, int x, int z) {
        return cells.get(key(dimension(level), x, z));
    }

    BlockPos origin(ServerLevel level) {
        Origin origin = origins.get(dimension(level));
        if (origin == null) return level.getRespawnData().pos();
        Cell cell = get(level, origin.x, origin.z);
        return new BlockPos(origin.x * 16 + 8, cell == null ? 80 : cell.y, origin.z * 16 + 8);
    }

    Cell at(ServerLevel level, BlockPos pos) {
        return get(level, Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    boolean unlocked(ServerLevel level, BlockPos pos) {
        Cell cell = at(level, pos);
        return cell != null && cell.unlocked;
    }

    Cell start(ServerLevel level, BlockPos pos) {
        String dimension = dimension(level);
        int x = Math.floorDiv(pos.getX(), 16);
        int z = Math.floorDiv(pos.getZ(), 16);
        if (!origins.containsKey(dimension)) origins.put(dimension, new Origin(x, z));
        Cell cell = cells.computeIfAbsent(key(dimension, x, z), ignored ->
                new Cell(dimension, x, z, pos.getY(), "minecraft:air", 0, true));
        if (!cell.unlocked) {
            cell.unlocked = true;
            cell.remaining = 0;
        }
        save();
        return cell;
    }

    Cell offer(ServerLevel level, int x, int z, int y) {
        String dimension = dimension(level);
        String key = key(dimension, x, z);
        Cell existing = cells.get(key);
        if (existing != null) return existing;
        if (!hasOpenNeighbor(level, x, z)) return null;
        int markerY = dimension.endsWith("overworld") ? 128 : 64;
        BlockPos center = new BlockPos(x * 16 + 8, markerY, z * 16 + 8);
        var biome = level.getBiome(center).unwrapKey().orElseThrow().identifier();
        if (dimension.endsWith("the_end") && biome.toString().equals("minecraft:the_end")
                && level.getEntitiesOfClass(EndCrystal.class,
                        new AABB(x * 16, level.getMinY(), z * 16,
                                x * 16 + 15.5, level.getMaxY(), z * 16 + 15.5)).isEmpty()) {
            Cell cell = new Cell(dimension, x, z, y, "minecraft:air", 0, true);
            cells.put(key, cell);
            save();
            return cell;
        }
        LootRoller.Offer roll = loot.roll(level, biome, center);
        int step = dimension.endsWith("the_end") ? 32 : 16;
        int multiplier = 1 + paid.getOrDefault(dimension, 0) / step;
        int count = Math.multiplyExact(roll.count(), multiplier);
        Cell cell = new Cell(dimension, x, z, y, roll.item().toString(), count, false);
        cells.put(key, cell);
        save();
        return cell;
    }

    boolean hasOpenNeighbor(ServerLevel level, int x, int z) {
        return open(level, x + 1, z) || open(level, x - 1, z)
                || open(level, x, z + 1) || open(level, x, z - 1);
    }

    boolean open(ServerLevel level, int x, int z) {
        Cell cell = get(level, x, z);
        return cell != null && cell.unlocked;
    }

    void unlock(Cell cell) {
        if (cell.unlocked) return;
        cell.unlocked = true;
        cell.remaining = 0;
        paid.merge(cell.dimension, 1, Integer::sum);
        save();
    }

    int totalPaid() {
        return paid.values().stream().mapToInt(Integer::intValue).sum();
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(new State(cells, origins, paid)), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot save Chunklock world state " + file, error);
        }
    }
}
