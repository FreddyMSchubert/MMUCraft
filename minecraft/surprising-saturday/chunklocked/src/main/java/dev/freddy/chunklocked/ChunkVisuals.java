package dev.freddy.chunklocked;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Locked chunk walls and floating item/count presentation. */
final class ChunkVisuals {
    private static final String[] BORDER_COLOURS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private ChunkVisuals() { }

    static void ensure(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell) {
        if (cell.unlocked) return;
        level.getChunk(cell.x, cell.z);
        AABB box = box(cell);
        int bottom = bottom(cell);
        int top = top(cell);
        var borders = level.getEntitiesOfClass(Display.BlockDisplay.class, box,
                entity -> entity.entityTags().contains(cell.tag()));
        boolean complete = borders.size() == (top - bottom) / 32;
        for (int y = bottom; complete && y < top; y += 32) {
            int sectionY = y;
            complete = borders.stream().anyMatch(entity -> Math.abs(entity.getY() - sectionY) < 0.1);
        }
        if (!complete) {
            borders.forEach(Entity::discard);
            walls(server, level, cell);
        }
        if (level.getEntitiesOfClass(Interaction.class, box,
                entity -> entity.entityTags().contains(cell.tag())).isEmpty()) {
            interactions(server, level, cell);
        }
        items(server, level, world, cell);
    }

    static void update(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box(cell),
                entity -> entity.entityTags().contains(cell.tag()) &&
                        (entity.entityTags().contains("cl_item") || entity.entityTags().contains("cl_count")))) {
            entity.discard();
        }
        items(server, level, world, cell);
    }

    static void move(ServerLevel level, ChunkWorld.Cell cell) {
        for (Display entity : level.getEntitiesOfClass(Display.class, box(cell),
                entity -> entity.entityTags().contains(cell.tag()) &&
                        (entity.entityTags().contains("cl_item") || entity.entityTags().contains("cl_count")))) {
            ServerPlayer closest = closestPlayer(level, entity.getX(), entity.getZ());
            if (closest != null) entity.setPos(entity.getX(), closest.getY()
                    + (entity.entityTags().contains("cl_count") ? 1.8 : 1.0), entity.getZ());
        }
    }

    private static ServerPlayer closestPlayer(ServerLevel level, double x, double z) {
        ServerPlayer closest = null;
        double best = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || player.isSpectator()) continue;
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                closest = player;
            }
        }
        return closest;
    }

    static void remove(ServerLevel level, ChunkWorld.Cell cell) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box(cell),
                candidate -> candidate.entityTags().contains(cell.tag()))) entity.discard();
    }

    static void recolour(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        for (Entity entity : level.getEntitiesOfClass(Display.BlockDisplay.class, box(cell),
                candidate -> candidate.entityTags().contains(cell.tag()))) entity.discard();
        walls(server, level, cell);
    }

    private static AABB box(ChunkWorld.Cell cell) {
        return new AABB(cell.x * 16 - 1, -80, cell.z * 16 - 1,
                cell.x * 16 + 17, 400, cell.z * 16 + 17);
    }

    private static void walls(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        String colour = cell.colour == null ? "light_blue" : cell.colour;
        int index = java.util.Arrays.asList(BORDER_COLOURS).indexOf(colour);
        if (index < 0) index = 3;
        String block = index < 12 ? "command_block" : "chain_command_block";
        int state = index % 12;
        String facing = new String[] {"north", "east", "south", "west", "up", "down"}[state % 6];
        String conditional = state >= 6 ? "true" : "false";
        int bottom = bottom(cell);
        int top = top(cell);
        double x = cell.x * 16 + 8;
        double z = cell.z * 16 + 8;
        for (int y = bottom; y < top; y += 32) {
            command(server, level, "summon minecraft:block_display " + x + " " + y + " " + z
                    + " {Tags:[\"chunklocked\",\"" + cell.tag()
                    + "\",\"cl_border\"],width:16.01f,height:32.01f,view_range:255f,"
                    + "brightness:{sky:15,block:15},block_state:{id:\"minecraft:" + block
                    + "\",properties:{facing:\"" + facing + "\",conditional:\"" + conditional + "\"}},"
                    + "transformation:{translation:[-8.0005f,-0.0005f,-8.0005f],"
                    + "scale:[16.001f,32.001f,16.001f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}");
        }
    }

    private static void interactions(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell) {
        int bottom = bottom(cell);
        int top = top(cell);
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

    private static int bottom(ChunkWorld.Cell cell) {
        return cell.dimension.endsWith("overworld") ? -64 : 0;
    }

    private static int top(ChunkWorld.Cell cell) {
        return cell.dimension.endsWith("the_nether") ? 128
                : cell.dimension.endsWith("the_end") ? 256 : 320;
    }

    private static void items(MinecraftServer server, ServerLevel level, ChunkWorld world,
            ChunkWorld.Cell cell) {
        String itemName = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(cell.item)))
                .getHoverName().getString();
        String label = (cell.remaining + " " + itemName).replace("\\", "\\\\").replace("\"", "\\\"");
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
            boolean hasItem = !level.getEntitiesOfClass(Display.ItemDisplay.class, box(cell),
                    entity -> entity.entityTags().contains(cell.tag())
                            && entity.entityTags().contains("cl_side_" + sideIndex)).isEmpty();
            boolean hasLabel = !level.getEntitiesOfClass(Display.TextDisplay.class, box(cell),
                    entity -> entity.entityTags().contains(cell.tag())
                            && entity.entityTags().contains("cl_side_" + sideIndex)
                            && entity.entityTags().contains("cl_named")).isEmpty();
            if (hasItem && hasLabel) continue;
            double[] side = sides[index];
            String sideTag = "cl_side_" + index;
            for (Entity entity : level.getEntitiesOfClass(Entity.class, box(cell),
                    entity -> entity.entityTags().contains(cell.tag())
                            && entity.entityTags().contains(sideTag)
                            && (entity.entityTags().contains("cl_item")
                                    || entity.entityTags().contains("cl_count")))) entity.discard();
            ServerPlayer closest = closestPlayer(level, side[0], side[1]);
            if (closest == null) continue;
            command(server, level, "summon minecraft:item_display " + side[0] + " " + (closest.getY() + 1.0)
                    + " " + side[1] + " {Tags:[\"chunklocked\",\"" + cell.tag()
                    + "\",\"cl_item\",\"" + sideTag + "\"],Glowing:1b,view_range:2f,"
                    + "brightness:{sky:7,block:7},Rotation:[" + yaw[index] + "f,0f],"
                    + "item:{id:\"" + cell.item + "\",count:1}}");
            command(server, level, "summon minecraft:text_display " + side[0] + " " + (closest.getY() + 1.8)
                    + " " + side[1] + " {Tags:[\"chunklocked\",\"" + cell.tag()
                    + "\",\"cl_count\",\"cl_named\",\"" + sideTag
                    + "\"],view_range:2f,line_width:300,"
                    + "brightness:{sky:15,block:15},Rotation:[" + yaw[index] + "f,0f],"
                    + "transformation:{translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]},"
                    + "text:\"" + label + "\",background:16777215,shadow:1b}");
        }
    }

    private static void command(MinecraftServer server, ServerLevel level, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }
}
