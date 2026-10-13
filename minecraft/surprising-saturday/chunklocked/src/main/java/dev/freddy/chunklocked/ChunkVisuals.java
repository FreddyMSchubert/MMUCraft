package dev.freddy.chunklocked;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Original Chunklock glass colours and floating item/count presentation. */
final class ChunkVisuals {
    private ChunkVisuals() { }

    static void ensure(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell, double playerY) {
        if (cell.unlocked) return;
        AABB box = box(cell);
        if (level.getEntitiesOfClass(Display.BlockDisplay.class, box,
                entity -> entity.entityTags().contains(cell.tag())).isEmpty()) {
            glass(server, level, cell);
        }
        if (level.getEntitiesOfClass(Interaction.class, box,
                entity -> entity.entityTags().contains(cell.tag())).isEmpty()) {
            interactions(server, level, cell);
        }
        items(server, level, world, cell, playerY);
    }

    static void update(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell, double playerY) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box(cell),
                entity -> entity.entityTags().contains(cell.tag()) &&
                        (entity.entityTags().contains("cl_item") || entity.entityTags().contains("cl_count")))) {
            entity.discard();
        }
        items(server, level, world, cell, playerY);
    }

    static void move(ServerLevel level, ChunkWorld.Cell cell, double playerY) {
        for (Display entity : level.getEntitiesOfClass(Display.class, box(cell),
                entity -> entity.entityTags().contains(cell.tag()) &&
                        (entity.entityTags().contains("cl_item") || entity.entityTags().contains("cl_count")))) {
            entity.setPos(entity.getX(), playerY + (entity.entityTags().contains("cl_count") ? 1.8 : 1.0), entity.getZ());
        }
    }

    static void remove(ServerLevel level, ChunkWorld.Cell cell) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box(cell),
                candidate -> candidate.entityTags().contains(cell.tag()))) entity.discard();
    }

    static void recolour(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        for (Entity entity : level.getEntitiesOfClass(Display.BlockDisplay.class, box(cell),
                candidate -> candidate.entityTags().contains(cell.tag()))) entity.discard();
        glass(server, level, cell);
    }

    private static AABB box(ChunkWorld.Cell cell) {
        return new AABB(cell.x * 16 - 1, -80, cell.z * 16 - 1,
                cell.x * 16 + 17, 400, cell.z * 16 + 17);
    }

    private static void glass(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        String colour = cell.colour == null ? "light_blue" : cell.colour;
        int[] yPositions = cell.dimension.endsWith("the_nether") ? new int[] {16, 48, 80, 112}
                : cell.dimension.endsWith("the_end") ? new int[] {64, 192}
                : new int[] {0, 64, 128, 256};
        int height = cell.dimension.endsWith("the_nether") ? 32
                : cell.dimension.endsWith("the_end") ? 128 : 64;
        for (int y : yPositions) {
            int h = cell.dimension.endsWith("overworld") && y >= 128 ? 128 : height;
            double x = cell.x * 16 + 8;
            double z = cell.z * 16 + 8;
            command(server, level, "summon minecraft:block_display " + x + " " + y + " " + z
                    + " {Tags:[\"chunklocked\",\"" + cell.tag() + "\",\"cl_glass\"],view_range:255f,"
                    + "brightness:{sky:10,block:10},block_state:{id:\"minecraft:" + colour + "_stained_glass\"},"
                    + "transformation:{translation:[-8.0005f,"
                    + (cell.dimension.endsWith("the_nether") ? "-16.0005f" : "-64.0005f") + ",-8.0005f],"
                    + "scale:[16.001f," + (h + 0.001) + "f,16.001f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}");
        }
    }

    private static void interactions(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        int bottom = cell.dimension.endsWith("overworld") ? -64 : 0;
        int top = cell.dimension.endsWith("the_nether") ? 128
                : cell.dimension.endsWith("the_end") ? 256 : 320;
        int lastShort = cell.dimension.endsWith("overworld") ? 192
                : cell.dimension.endsWith("the_end") ? 128 : top;
        for (int y = bottom; y < top; y += 16) {
            if (y > lastShort) break;
            int height = y == lastShort ? top - y : 16;
            command(server, level, "summon minecraft:interaction " + (cell.x * 16 + 8)
                    + " " + y + " " + (cell.z * 16 + 8)
                    + " {Tags:[\"chunklocked\",\"" + cell.tag() + "\",\"cl_interaction\"],"
                    + "width:16.0001f,height:" + height + "f,response:1b}");
        }
    }

    private static void items(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell, double playerY) {
        double minX = cell.x * 16;
        double minZ = cell.z * 16;
        double[][] sides = {
                {minX + 8, minZ - 0.1}, {minX + 16.1, minZ + 8},
                {minX + 8, minZ + 16.1}, {minX - 0.1, minZ + 8}
        };
        int[][] neighbors = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int[] yaw = {180, 270, 0, 90};
        for (int index = 0; index < sides.length; index++) {
            if (!world.open(level, cell.x + neighbors[index][0], cell.z + neighbors[index][1])) continue;
            int sideIndex = index;
            if (!level.getEntitiesOfClass(Display.ItemDisplay.class, box(cell),
                    entity -> entity.entityTags().contains(cell.tag())
                            && entity.entityTags().contains("cl_side_" + sideIndex)).isEmpty()) continue;
            double[] side = sides[index];
            String sideTag = "cl_side_" + index;
            command(server, level, "summon minecraft:item_display " + side[0] + " " + (playerY + 1.0)
                    + " " + side[1] + " {Tags:[\"chunklocked\",\"" + cell.tag()
                    + "\",\"cl_item\",\"" + sideTag + "\"],Glowing:1b,view_range:0.2f,"
                    + "brightness:{sky:7,block:7},Rotation:[" + yaw[index] + "f,0f],"
                    + "item:{id:\"" + cell.item + "\",count:1}}");
            command(server, level, "summon minecraft:text_display " + side[0] + " " + (playerY + 1.8)
                    + " " + side[1] + " {Tags:[\"chunklocked\",\"" + cell.tag()
                    + "\",\"cl_count\",\"" + sideTag + "\"],view_range:0.2f,"
                    + "brightness:{sky:15,block:15},Rotation:[" + yaw[index] + "f,0f],"
                    + "transformation:{translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]},"
                    + "text:\"" + cell.remaining + "\",background:16777215,shadow:1b}");
        }
    }

    private static void command(MinecraftServer server, ServerLevel level, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }
}
