package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

public class CosmeticManager
{
    public static final String COSMETIC_STORED_ARMOR_ID = "stored_armor";
    public static final int COSMETIC_STORED_ARMOR_EMPTY = 0;
    public static final int COSMETIC_STORED_ARMOR_TURTLE = 1;
    public static final int COSMETIC_STORED_ARMOR_LEATHER = 2;
    public static final int COSMETIC_STORED_ARMOR_COPPER = 3;
    public static final int COSMETIC_STORED_ARMOR_CHAINMAIL = 4;
    public static final int COSMETIC_STORED_ARMOR_GOLD = 5;
    public static final int COSMETIC_STORED_ARMOR_IRON = 6;
    public static final int COSMETIC_STORED_ARMOR_DIAMOND = 7;
    public static final int COSMETIC_STORED_ARMOR_NETHERITE = 8;

    public static ItemStack getStoredArmorFromCosmetic(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int storedArmorId = tag.getIntOr(COSMETIC_STORED_ARMOR_ID, 0);
        if (storedArmorId == COSMETIC_STORED_ARMOR_EMPTY) return ItemStack.EMPTY;

        return switch (storedArmorId) {
            case COSMETIC_STORED_ARMOR_TURTLE -> ItemStacks
        }
    }
}
