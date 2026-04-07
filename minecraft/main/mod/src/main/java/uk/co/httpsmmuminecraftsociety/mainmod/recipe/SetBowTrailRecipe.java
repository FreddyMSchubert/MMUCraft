package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails.BowTrailData;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SetBowTrailRecipe extends CustomRecipe
{
    private static final String DYE_ID = "dye";
    private static final String WEIGHT_ID = "weight";

    @Override
    public boolean matches(CraftingInput inputs, Level level)
    {
        List<ItemStack> stacks = inputs.items();

        int nonEmptyCount = 0;
        boolean hasBow = false;

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.is(Items.BOW)) {
                if (hasBow) return false;
                hasBow = true;
                continue;
            }

            if (stack.getItem() instanceof DyeItem) {
                continue;
            }

            return false;
        }

        // bow alone is valid and means "clear the trail"
        return nonEmptyCount >= 1 && hasBow;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack bow = findBow(input);
        if (bow.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = bow.copy();
        result.setCount(1);

        Map<DyeColor, Integer> dyeWeights = collectDyeWeights(input);

        if (dyeWeights.isEmpty()) {
            clearTrailData(result);
        } else {
            setTrailData(result, dyeWeights);
        }

        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inputs)
    {
        return NonNullList.withSize(inputs.size(), ItemStack.EMPTY);
    }

    private static ItemStack findBow(CraftingInput input)
    {
        for (ItemStack stack : input.items()) {
            if (stack.is(Items.BOW)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static Map<DyeColor, Integer> collectDyeWeights(CraftingInput input)
    {
        Map<DyeColor, Integer> weights = new EnumMap<>(DyeColor.class);

        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof DyeItem dyeItem) {
                DyeColor color = stack.get(DataComponents.DYE);
                if (color != null) {
                    weights.merge(color, 1, Integer::sum);
                }
            }
        }

        return weights;
    }

    private static void setTrailData(ItemStack stack, Map<DyeColor, Integer> dyeWeights)
    {
        CompoundTag rootTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag trailList = new ListTag();

        for (Map.Entry<DyeColor, Integer> entry : dyeWeights.entrySet()) {
            int weight = entry.getValue();
            if (weight <= 0) continue;

            CompoundTag dyeTag = new CompoundTag();
            dyeTag.putString(DYE_ID, entry.getKey().getSerializedName());
            dyeTag.putInt(WEIGHT_ID, weight);
            trailList.add(dyeTag);
        }

        rootTag.put(BowTrailData.BOW_TRAIL_DATA_KEY, trailList);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));
    }

    private static void clearTrailData(ItemStack stack)
    {
        CompoundTag rootTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (!rootTag.contains(BowTrailData.BOW_TRAIL_DATA_KEY)) {
            return;
        }

        rootTag.remove(BowTrailData.BOW_TRAIL_DATA_KEY);

        if (rootTag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));
        }
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.SET_BOW_TRAIL_SERIALIZER;
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
