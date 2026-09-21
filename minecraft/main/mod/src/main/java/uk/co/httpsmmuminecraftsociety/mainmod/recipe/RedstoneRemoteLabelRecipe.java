package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.RedstoneRemoteCharm;

public final class RedstoneRemoteLabelRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ingredients(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Ingredients ingredients = ingredients(input);
        if (ingredients == null) return ItemStack.EMPTY;
        ItemStack result = ingredients.remote().copyWithCount(1);
        RedstoneRemoteCharm.labelSelectedFrequency(result, ingredients.label());
        return result;
    }

    private static Ingredients ingredients(CraftingInput input) {
        ItemStack remote = ItemStack.EMPTY;
        String label = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (RedstoneRemoteCharm.isRemote(stack) && remote.isEmpty()) {
                remote = stack;
            } else if (stack.is(Items.NAME_TAG) && label == null) {
                Component customName = stack.get(DataComponents.CUSTOM_NAME);
                if (customName == null || customName.getString().isBlank()) return null;
                label = customName.getString();
            } else {
                return null;
            }
        }
        return remote.isEmpty() || label == null ? null : new Ingredients(remote, label);
    }

    private record Ingredients(ItemStack remote, String label) {}

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.REDSTONE_REMOTE_LABEL_SERIALIZER;
    }
}
