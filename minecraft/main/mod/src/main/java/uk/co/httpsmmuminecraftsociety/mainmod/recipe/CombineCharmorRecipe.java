package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

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

public class CombineCharmorRecipe extends CustomRecipe
{
    private record CraftingInfo(
            boolean craftable,
            ItemStack armor,
            ItemStack charm,
            FakeItem charmFakeItem,
            StoredCharmData storedCharm
    ) {}

    private CraftingInfo getCraftingInfo(CraftingInput input)
    {
        ItemStack armor = null;
        ItemStack charm = null;
        FakeItem charmFakeItem = null;
        StoredCharmData storedCharm = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
                if (armor != null || !CharmorManager.canEquipMoreCharms(stack)) {
                    continue;
                }
                armor = stack;
                continue;
            }

            StoredCharmData stackCharm = CharmStackData.getSingleStoredCharm(stack).orElse(null);
            if (stackCharm == null) {
                continue;
            }

            FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(stackCharm.charmId());
            if (fakeItem == null || fakeItem.getFeature(CharmItemFeature.class) == null) {
                continue;
            }

            if (charm != null) {
                continue;
            }

            charm = stack;
            charmFakeItem = fakeItem;
            storedCharm = stackCharm;
        }

        return new CraftingInfo(armor != null && charm != null, armor, charm, charmFakeItem, storedCharm);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return getCraftingInfo(input).craftable && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        CraftingInfo cinfo = getCraftingInfo(input);
        if (!cinfo.craftable) {
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = cinfo.armor.copy();
        List<StoredCharmData> armorCharms = new ArrayList<>(CharmStackData.getStoredCharms(resultStack));

        if (armorCharms.stream().anyMatch(c -> c.charmId() == cinfo.storedCharm.charmId())) {
            return ItemStack.EMPTY;
        }

        armorCharms.add(cinfo.storedCharm);
        CharmStackData.setStoredCharms(resultStack, armorCharms);

        CharmItemFeature charmFeature = cinfo.charmFakeItem.getFeature(CharmItemFeature.class);
        if (charmFeature != null
                && cinfo.storedCharm.level() > 0
                && charmFeature.charm() instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(resultStack, cinfo.storedCharm.level());
        }

        CharmorManager.updateArmorTooltip(resultStack);
        return resultStack;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.COMBINE_CHARMOR_SERIALIZER;
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
