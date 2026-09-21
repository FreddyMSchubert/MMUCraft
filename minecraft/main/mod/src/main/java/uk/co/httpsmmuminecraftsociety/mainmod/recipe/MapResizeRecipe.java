package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

public final class MapResizeRecipe extends CustomRecipe {
    private static final int SHEARS_DAMAGE = 16;

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack map = ItemStack.EMPTY;
        ItemStack resizingItem = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (SmallMaps.isMap(stack) && map.isEmpty()) map = stack;
            else if ((stack.is(Items.SHEARS) || stack.is(Items.PAPER)) && resizingItem.isEmpty()) resizingItem = stack;
            else return false;
        }
        if (map.isEmpty() || resizingItem.isEmpty()) return false;
        return resizingItem.is(Items.SHEARS)
                ? SmallMaps.canZoomIn(map, level)
                : SmallMaps.canZoomOut(map, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack map = input.items().stream().filter(SmallMaps::isMap).findFirst().orElse(ItemStack.EMPTY);
        boolean zoomIn = input.items().stream().anyMatch(stack -> stack.is(Items.SHEARS));
        return map.isEmpty() ? ItemStack.EMPTY : SmallMaps.craftingResult(map, zoomIn);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        for (int index = 0; index < input.size(); index++) {
            ItemStack ingredient = input.getItem(index);
            if (!ingredient.is(Items.SHEARS)) continue;
            ItemStack shears = ingredient.copyWithCount(1);
            shears.setDamageValue(shears.getDamageValue() + SHEARS_DAMAGE);
            if (shears.getDamageValue() < shears.getMaxDamage()) remaining.set(index, shears);
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.MAP_RESIZE_SERIALIZER;
    }
}
