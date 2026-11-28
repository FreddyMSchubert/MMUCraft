package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.Presents;

import java.util.ArrayList;
import java.util.List;

public final class PresentRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) return false;
        return input.items().stream().anyMatch(Presents::isPaper);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack paper = ItemStack.EMPTY;
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (paper.isEmpty() && Presents.isPaper(stack)) paper = stack;
            else contents.add(stack.copyWithCount(1));
        }
        return Presents.wrap(paper, contents);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.PRESENT_SERIALIZER;
    }
}
