package uk.co.httpsmmuminecraftsociety.mainmod.maps;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Per-map block transparency applied while Minecraft samples new map pixels. */
public final class MapInvisibility {
    public static final String INVISI_CARROT_ID = "charm-invisi-carrot";
    private static final String BLOCK_TAG = "mainmod_invisible_map_block";
    private static final ThreadLocal<Block> UPDATING_BLOCK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TRANSPARENT_PIXEL = ThreadLocal.withInitial(() -> false);

    private MapInvisibility() {}

    public static void setTarget(ItemStack map, Block block) {
        CustomData.update(DataComponents.CUSTOM_DATA, map,
                tag -> tag.putString(BLOCK_TAG, BuiltInRegistries.BLOCK.getKey(block).toString()));
    }

    public static Block target(ItemStack map) {
        String id = map.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString(BLOCK_TAG).orElse("");
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? null : BuiltInRegistries.BLOCK.getValue(identifier);
    }

    public static void copyTarget(ItemStack from, ItemStack to) {
        Block target = target(from);
        if (target != null) setTarget(to, target);
    }

    public static void beginUpdate(ItemStack map) {
        UPDATING_BLOCK.set(target(map));
        TRANSPARENT_PIXEL.set(false);
    }

    public static void endUpdate() {
        UPDATING_BLOCK.remove();
        TRANSPARENT_PIXEL.remove();
    }

    public static void beginPixel() {
        TRANSPARENT_PIXEL.set(false);
    }

    public static MapColor observeMapColor(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           net.minecraft.core.BlockPos pos) {
        Block target = UPDATING_BLOCK.get();
        if (target != null && state.is(target)) TRANSPARENT_PIXEL.set(true);
        return state.getMapColor(level, pos);
    }

    public static byte applyTransparency(byte vanillaColor) {
        return TRANSPARENT_PIXEL.get() ? 0 : vanillaColor;
    }

    public static void updateVanillaMap(ItemStack map, ServerLevel level, Player player) {
        MapItemSavedData data = MapItem.getSavedData(map, level);
        if (data == null) return;
        beginUpdate(map);
        try {
            ((MapItem) Items.FILLED_MAP).update(level, player, data);
        } finally {
            endUpdate();
        }
    }

    public static InteractionResult useTaggedEmptyMap(ServerLevel level, Player player, ItemStack emptyMap) {
        if (target(emptyMap) == null) return null;
        CompoundTag customData = emptyMap.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        emptyMap.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(Items.MAP));
        level.playSound(
                null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F, 1.0F);
        ItemStack filled = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
        filled.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        SmallMaps.refreshTooltip(filled, level);
        if (emptyMap.isEmpty()) return InteractionResult.SUCCESS.heldItemTransformedTo(filled);
        if (!player.getInventory().add(filled.copy())) player.drop(filled, false);
        return InteractionResult.SUCCESS;
    }
}
