package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.SculkPhialCharm;

import java.util.List;

public class ExtractXPFromPhialRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput inputs, Level level)
    {
        List<ItemStack> stacks = inputs.items();

        int nonEmptyCount = 0;
        boolean hasGlassBottle = false;
        boolean hasFilledPhial = false;

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.is(Items.GLASS_BOTTLE)) {
                if (hasGlassBottle) return false;
                hasGlassBottle = true;
                continue;
            }

            if (isFilledSculkPhial(stack)) {
                if (hasFilledPhial) return false;
                hasFilledPhial = true;
                continue;
            }

            return false;
        }

        return nonEmptyCount == 2 && hasGlassBottle && hasFilledPhial;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack phial = findFilledPhial(input);
        if (phial.isEmpty()) return ItemStack.EMPTY;

        int storedXp = getStoredXp(phial);
        if (storedXp <= 0) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(Items.EXPERIENCE_BOTTLE, 1);

        CompoundTag resultTag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        resultTag.putInt(SculkPhialCharm.XP_STORED_ID, storedXp);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(resultTag));

        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inputs)
    {
        List<ItemStack> stacks = inputs.items();
        NonNullList<ItemStack> remainders = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            if (isSculkPhial(stack)) {
                ItemStack emptyPhial = stack.copy();
                emptyPhial.setCount(1);

                CompoundTag tag = emptyPhial.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putInt(SculkPhialCharm.XP_STORED_ID, 0);
                emptyPhial.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                remainders.set(i, emptyPhial);
                break;
            }
        }

        return remainders;
    }

    private static ItemStack findFilledPhial(CraftingInput inputs) {
        for (ItemStack stack : inputs.items()) {
            if (isFilledSculkPhial(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isFilledSculkPhial(ItemStack stack) {
        return isSculkPhial(stack) && getStoredXp(stack) > 0;
    }

    private static boolean isSculkPhial(ItemStack stack) {
        if (stack.isEmpty()) return false;

        List<FakeItem> abilities = CharmsManager.getAbilitiesFromItemStack(stack);
        for (FakeItem fitem : abilities) {
            CharmItemFeature cif = fitem.getFeature(CharmItemFeature.class);
            if (cif != null && cif.charm() instanceof SculkPhialCharm) {
                return true;
            }
        }

        return false;
    }

    private static int getStoredXp(ItemStack stack) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return nbt.getIntOr(SculkPhialCharm.XP_STORED_ID, 0);
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.EXTRACT_XP_FROM_PHIAL_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}