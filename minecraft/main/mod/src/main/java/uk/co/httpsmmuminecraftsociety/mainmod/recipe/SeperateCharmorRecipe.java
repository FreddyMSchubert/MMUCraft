package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;

public class SeperateCharmorRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.ingredientCount() > 1) {
            return false;
        }

        ItemStack stack = recipeInput.items().getFirst();

        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            return false;
        }

        return !CharmStackData.getStoredCharms(stack).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack inputStack = input.items().getFirst();
        List<StoredCharmData> charms = CharmStackData.getStoredCharms(inputStack);
        if (charms.isEmpty()) {
            return ItemStack.EMPTY;
        }

        StoredCharmData removedCharm = charms.getLast();
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(removedCharm.charmId());
        if (fakeItem == null) {
            return ItemStack.EMPTY;
        }

        return fakeItem.createItemStackAtLevel(removedCharm.level());
    }

    private static ItemStack removeLastCharmFromStack(ItemStack stack) {
        List<StoredCharmData> charms = new ArrayList<>(CharmStackData.getStoredCharms(stack));
        if (charms.isEmpty()) {
            return stack;
        }

        StoredCharmData removedCharm = charms.removeLast();

        FakeItem removedCharmItem = FakeItems.CHARM_ID_MAP.get(removedCharm.charmId());
        CharmItemFeature removedFeature = removedCharmItem != null
                ? removedCharmItem.getFeature(CharmItemFeature.class)
                : null;

        if (removedFeature != null
                && removedCharm.level() > 0
                && removedFeature.charm() instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.disableEffectForItem(stack, removedCharm.level());
        }

        CharmStackData.setStoredCharms(stack, charms);
        CharmorManager.updateArmorTooltip(stack);

        return stack;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput craftingInput)
    {
        NonNullList<ItemStack> list = NonNullList.withSize(craftingInput.size(), ItemStack.EMPTY);

        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i).copy();
            if (!stack.isEmpty()) {
                list.set(i, removeLastCharmFromStack(stack));
            }
        }

        return list;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.SEPERATE_CHARMOR_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
