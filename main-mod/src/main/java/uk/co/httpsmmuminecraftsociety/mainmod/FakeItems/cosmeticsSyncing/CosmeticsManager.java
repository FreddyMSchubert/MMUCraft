package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.cosmeticsSyncing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class CosmeticsManager {
    public static final String COSMETIC_ASSET_ID = "cosmetic_asset_path";

    private CosmeticsManager() {}

    public static void setCosmeticAssetId(ItemStack stack, @Nullable String assetId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (assetId == null || assetId.isBlank()) {
            tag.remove(COSMETIC_ASSET_ID);
        } else {
            tag.putString(COSMETIC_ASSET_ID, assetId);
        }

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static @Nullable String getCosmeticAssetId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(COSMETIC_ASSET_ID)) {
            return null;
        }
        return tag.getString(COSMETIC_ASSET_ID).orElse(null);
    }
}