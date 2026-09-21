package uk.co.httpsmmuminecraftsociety.mainmod.maps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps.MapItemSavedDataInvoker;

import java.util.ArrayList;
import java.util.List;

/** Server-side small maps backed by the vanilla 128x128 map image. */
public final class SmallMaps {
    public static final int VANILLA_SIZE = 128;
    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 2048;

    private static final String SIZE_TAG = "mainmod_map_size";
    private static final String OPERATION_TAG = "mainmod_map_operation";
    private static final String ZOOM_IN = "zoom_in";
    private static final String ZOOM_OUT = "zoom_out";
    private static final String SIZE_TOOLTIP_PREFIX = "Map size: ";
    private static final String INVISIBLE_TOOLTIP_PREFIX = "Invisible block: ";

    private SmallMaps() {}

    public static boolean isMap(ItemStack stack) {
        return stack.is(Items.MAP) || stack.is(Items.FILLED_MAP);
    }

    public static boolean isSmall(ItemStack stack) {
        return customSize(stack) < VANILLA_SIZE;
    }

    public static int size(ItemStack stack, Level level) {
        int customSize = customSize(stack);
        if (customSize != VANILLA_SIZE) return customSize;
        if (!stack.is(Items.FILLED_MAP)) return VANILLA_SIZE;
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        return data == null ? VANILLA_SIZE : VANILLA_SIZE << data.scale;
    }

    public static ItemLore defaultLore() {
        return new ItemLore(List.of(sizeLine(VANILLA_SIZE)));
    }

    public static void refreshTooltip(ItemStack stack, Level level) {
        if (!isMap(stack)) return;
        refreshStoredTooltip(stack, size(stack, level));
    }

    public static void refreshTooltipFromStoredSize(ItemStack stack) {
        int storedSize = storedTooltipSize(stack);
        if (storedSize > 0) refreshStoredTooltip(stack, storedSize);
    }

    public static boolean canZoomIn(ItemStack stack, Level level) {
        return isMap(stack) && size(stack, level) > MIN_SIZE;
    }

    public static boolean canZoomOut(ItemStack stack, Level level) {
        return isMap(stack) && size(stack, level) < MAX_SIZE;
    }

    public static ItemStack craftingResult(ItemStack source, boolean zoomIn) {
        ItemStack result = source.copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, result,
                tag -> tag.putString(OPERATION_TAG, zoomIn ? ZOOM_IN : ZOOM_OUT));
        int oldSize = storedTooltipSize(source);
        if (oldSize > 0) refreshStoredTooltip(result, zoomIn ? oldSize / 2 : oldSize * 2);
        return result;
    }

    public static void processCraftedMap(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String operation = tag.getString(OPERATION_TAG).orElse("");
        if (operation.isEmpty()) return;
        tag.remove(OPERATION_TAG);
        writeCustomData(stack, tag);

        int oldSize = size(stack, level);
        int newSize = ZOOM_IN.equals(operation) ? oldSize / 2 : oldSize * 2;
        if (newSize < MIN_SIZE || newSize > MAX_SIZE) return;

        if (stack.is(Items.MAP)) {
            setCustomSize(stack, newSize);
            refreshStoredTooltip(stack, newSize);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) return;

        MapItemSavedData source = MapItem.getSavedData(stack, serverLevel);
        if (source == null) return;
        if (ZOOM_IN.equals(operation)) {
            zoomFilledMapIn(stack, serverLevel, source, oldSize, newSize);
        } else {
            zoomFilledMapOut(stack, serverLevel, source, newSize);
        }
        refreshStoredTooltip(stack, newSize);
    }

    public static InteractionResult useResizedEmptyMap(Level level, Player player, ItemStack emptyMap) {
        int size = customSize(emptyMap);
        if (size == VANILLA_SIZE) return null;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        emptyMap.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(Items.MAP));
        serverLevel.playSound(
                null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F, 1.0F);
        CompoundTag customData = emptyMap.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ItemStack filled = size < VANILLA_SIZE
                ? new ItemStack(Items.FILLED_MAP)
                : MapItem.create(serverLevel, player.getBlockX(), player.getBlockZ(), scaleFor(size), true, false);
        if (!customData.isEmpty()) filled.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        setCustomSize(filled, size);
        if (size < VANILLA_SIZE) {
            int centerX = alignedCenter(player.getBlockX(), size);
            int centerZ = alignedCenter(player.getBlockZ(), size);
            MapItemSavedData sampled = sample(serverLevel, centerX, centerZ, size, MapInvisibility.target(filled));
            setMapData(filled, serverLevel, sampled.locked());
        }
        refreshStoredTooltip(filled, size);

        if (emptyMap.isEmpty()) return InteractionResult.SUCCESS.heldItemTransformedTo(filled);
        if (!player.getInventory().add(filled.copy())) player.drop(filled, false);
        return InteractionResult.SUCCESS;
    }

    public static void refreshSmallMap(ItemStack stack, ServerLevel level) {
        int size = customSize(stack);
        if (size >= VANILLA_SIZE) return;
        MapItemSavedData current = MapItem.getSavedData(stack, level);
        if (current == null) return;
        ServerLevel mapLevel = level.getServer().getLevel(current.dimension);
        if (mapLevel == null) return;
        int centerX = alignedCenter(current.centerX, size);
        int centerZ = alignedCenter(current.centerZ, size);
        MapItemSavedData refreshed = sample(
                mapLevel, centerX, centerZ, size, MapInvisibility.target(stack)).locked();
        setMapData(stack, level, refreshed);
        refreshStoredTooltip(stack, size);
    }

    private static void zoomFilledMapIn(ItemStack stack, ServerLevel level, MapItemSavedData source,
                                        int oldSize, int newSize) {
        int centerX = source.centerX - oldSize / 4;
        int centerZ = source.centerZ - oldSize / 4;
        byte scale = scaleFor(newSize);
        MapItemSavedData target = createData(centerX, centerZ, scale, source.dimension, newSize >= VANILLA_SIZE);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                target.setColor(x, y, source.colors[(y / 2) * 128 + x / 2]);
            }
        }
        setCustomSize(stack, newSize);
        setMapData(stack, level, newSize < VANILLA_SIZE ? target.locked() : target);
    }

    private static void zoomFilledMapOut(ItemStack stack, ServerLevel level, MapItemSavedData source, int newSize) {
        int centerX = alignedCenter(source.centerX, newSize);
        int centerZ = alignedCenter(source.centerZ, newSize);
        MapItemSavedData target;
        if (newSize < VANILLA_SIZE) {
            ServerLevel mapLevel = level.getServer().getLevel(source.dimension);
            target = mapLevel == null
                    ? createData(centerX, centerZ, (byte) 0, source.dimension, false).locked()
                    : sample(mapLevel, centerX, centerZ, newSize, MapInvisibility.target(stack)).locked();
        } else {
            target = createData(centerX, centerZ, scaleFor(newSize), source.dimension, true);
        }
        setCustomSize(stack, newSize);
        setMapData(stack, level, target);
    }

    private static MapItemSavedData sample(ServerLevel level, int centerX, int centerZ, int size,
                                           net.minecraft.world.level.block.Block invisibleBlock) {
        MapItemSavedData data = createData(centerX, centerZ, (byte) 0, level.dimension(), false);
        int repeat = VANILLA_SIZE / size;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (int logicalX = 0; logicalX < size; logicalX++) {
            double previousHeight = 0.0;
            int worldX = centerX - size / 2 + logicalX;
            for (int logicalZ = 0; logicalZ < size; logicalZ++) {
                int worldZ = centerZ - size / 2 + logicalZ;
                Sample sample = sampleColor(level, worldX, worldZ, pos, below, invisibleBlock);
                MapColor.Brightness brightness = brightness(sample, previousHeight, logicalX + logicalZ);
                previousHeight = sample.height();
                byte color = sample.color().getPackedId(brightness);
                int startX = logicalX * repeat;
                int startZ = logicalZ * repeat;
                for (int dy = 0; dy < repeat; dy++) {
                    for (int dx = 0; dx < repeat; dx++) data.setColor(startX + dx, startZ + dy, color);
                }
            }
        }
        return data;
    }

    private static Sample sampleColor(ServerLevel level, int x, int z, BlockPos.MutableBlockPos pos,
                                      BlockPos.MutableBlockPos below,
                                      net.minecraft.world.level.block.Block invisibleBlock) {
        if (level.dimensionType().hasCeiling()) {
            int noise = x + z * 231871;
            noise = noise * noise * 31287121 + noise * 11;
            MapColor color = ((noise >> 20) & 1) == 0 ? MapColor.DIRT : MapColor.STONE;
            return new Sample(color, 100.0, 0);
        }

        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        BlockState state;
        do {
            pos.set(x, --y, z);
            state = level.getBlockState(pos);
        } while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinY());
        if (y <= level.getMinY()) state = Blocks.BEDROCK.defaultBlockState();

        if (invisibleBlock != null && state.is(invisibleBlock)) {
            return new Sample(MapColor.NONE, y, 0);
        }

        int fluidDepth = 0;
        if (!state.getFluidState().isEmpty()) {
            int belowY = y - 1;
            BlockState belowState;
            do {
                below.set(x, belowY--, z);
                belowState = level.getBlockState(below);
                fluidDepth++;
            } while (belowY > level.getMinY() && !belowState.getFluidState().isEmpty());
            if (!state.isFaceSturdy(level, pos, Direction.UP)) state = state.getFluidState().createLegacyBlock();
        }
        return new Sample(state.getMapColor(level, pos), y, fluidDepth);
    }

    private static MapColor.Brightness brightness(Sample sample, double previousHeight, int parity) {
        if (sample.color() == MapColor.WATER) {
            double depth = sample.fluidDepth() * 0.1 + (parity & 1) * 0.2;
            if (depth < 0.5) return MapColor.Brightness.HIGH;
            return depth > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        double slope = (sample.height() - previousHeight) * 4.0 / 5.0 + ((parity & 1) - 0.5) * 0.4;
        if (slope > 0.6) return MapColor.Brightness.HIGH;
        return slope < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    private static MapItemSavedData createData(int centerX, int centerZ, byte scale,
                                               net.minecraft.resources.ResourceKey<Level> dimension,
                                               boolean trackingPosition) {
        return MapItemSavedDataInvoker.mainmod$create(
                centerX, centerZ, scale, trackingPosition, false, false, dimension);
    }

    private static void setMapData(ItemStack stack, ServerLevel level, MapItemSavedData data) {
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        stack.set(DataComponents.MAP_ID, id);
    }

    private static byte scaleFor(int size) {
        return (byte) (size <= VANILLA_SIZE ? 0 : Integer.numberOfTrailingZeros(size / VANILLA_SIZE));
    }

    private static int alignedCenter(int coordinate, int size) {
        return Math.floorDiv(coordinate, size) * size + size / 2;
    }

    private static void refreshStoredTooltip(ItemStack stack, int size) {
        ItemLore oldLore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(oldLore.lines().stream()
                .filter(line -> !line.getString().startsWith(SIZE_TOOLTIP_PREFIX))
                .filter(line -> !line.getString().startsWith(INVISIBLE_TOOLTIP_PREFIX))
                .toList());
        lines.add(sizeLine(size));
        net.minecraft.world.level.block.Block invisibleBlock = MapInvisibility.target(stack);
        if (invisibleBlock != null) {
            lines.add(Component.literal(INVISIBLE_TOOLTIP_PREFIX)
                    .append(invisibleBlock.getName())
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }
        ItemLore newLore = new ItemLore(lines);
        if (!newLore.equals(oldLore)) stack.set(DataComponents.LORE, newLore);
    }

    private static Component sizeLine(int size) {
        return Component.literal(SIZE_TOOLTIP_PREFIX + size + "x" + size + " blocks")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    private static int storedTooltipSize(ItemStack stack) {
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            String text = line.getString();
            if (!text.startsWith(SIZE_TOOLTIP_PREFIX)) continue;
            int separator = text.indexOf('x', SIZE_TOOLTIP_PREFIX.length());
            if (separator < 0) continue;
            try {
                return Integer.parseInt(text.substring(SIZE_TOOLTIP_PREFIX.length(), separator));
            } catch (NumberFormatException ignored) {
                // Fall through to component data.
            }
        }
        return stack.is(Items.MAP) || customSize(stack) != VANILLA_SIZE ? customSize(stack) : 0;
    }

    private static int customSize(ItemStack stack) {
        int value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(SIZE_TAG, VANILLA_SIZE);
        return value == 16 || value == 32 || value == 64 || value == 256
                || value == 512 || value == 1024 || value == MAX_SIZE ? value : VANILLA_SIZE;
    }

    private static void setCustomSize(ItemStack stack, int size) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (size < VANILLA_SIZE || stack.is(Items.MAP) && size > VANILLA_SIZE) tag.putInt(SIZE_TAG, size);
        else tag.remove(SIZE_TAG);
        writeCustomData(stack, tag);
    }

    private static void writeCustomData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record Sample(MapColor color, double height, int fluidDepth) {}
}
