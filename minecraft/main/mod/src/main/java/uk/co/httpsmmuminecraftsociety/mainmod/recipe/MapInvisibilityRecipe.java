package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.MapInvisibility;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

public final class MapInvisibilityRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ingredients(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Ingredients ingredients = ingredients(input);
        if (ingredients == null) return ItemStack.EMPTY;
        ItemStack result = ingredients.map().copyWithCount(1);
        MapInvisibility.setTarget(result, ingredients.block().getBlock());
        SmallMaps.refreshTooltipFromStoredSize(result);
        return result;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.MAP_INVISIBILITY_SERIALIZER;
    }

    private static Ingredients ingredients(CraftingInput input) {
        ItemStack map = ItemStack.EMPTY;
        BlockItem block = null;
        boolean carrot = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (SmallMaps.isMap(stack) && map.isEmpty()) {
                map = stack;
            } else if (stack.getItem() instanceof BlockItem blockItem && block == null) {
                block = blockItem;
            } else if (FakeItems.isSpecificFakeItem(stack, MapInvisibility.INVISI_CARROT_ID) && !carrot) {
                carrot = true;
            } else {
                return null;
            }
        }
        return map.isEmpty() || block == null || !carrot ? null : new Ingredients(map, block);
    }

    private record Ingredients(ItemStack map, BlockItem block) {}
}
