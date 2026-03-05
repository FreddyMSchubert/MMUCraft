package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

public class CombineCharmorRecipe implements CraftingRecipe
{
    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        // must have 1x armor item with a free slot
        boolean armorFound = false;
        // and 1x charm
        boolean charmFound = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);

            if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
                if (armorFound || !CharmorManager.canEquipMoreCharms(stack)) return false;
                armorFound = true;
                continue;
            }

            CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
            if (!cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("cosmetic-charm-")) {
                if (charmFound) return false;
                charmFound = true;
                continue;
            }
        }

        return armorFound && charmFound;
    }

    @Override
    public ItemStack assemble(CraftingInput recipeInput, HolderLookup.Provider provider)
    {
        return null;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return null;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.EQUIPMENT;
    }
}
