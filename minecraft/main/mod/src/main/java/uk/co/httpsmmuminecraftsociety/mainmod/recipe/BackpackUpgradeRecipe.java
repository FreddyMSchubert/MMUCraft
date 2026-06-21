package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.Item;
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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.BackpackCharm;

import java.util.List;

public class BackpackUpgradeRecipe extends CustomRecipe {
    private static final int GRID_SIZE = 9;
    private static final int CENTER_SLOT = 4;
    private static final List<Integer> CORNERS = List.of(0, 2, 6, 8);
    private static final List<Integer> EDGES = List.of(1, 3, 5, 7);

    private record Match(BackpackCharm.Tier resultTier, ItemStack sourceBackpack) {}

    private static Match getMatch(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != GRID_SIZE) {
            return null;
        }

        if (matchesLeatherBackpack(input)) {
            return new Match(BackpackCharm.Tier.LEATHER, ItemStack.EMPTY);
        }
        if (matchesAlternatingUpgrade(input, BackpackCharm.Tier.LEATHER, Items.IRON_INGOT, Items.COPPER_BLOCK.weathering().unaffected())) {
            return new Match(BackpackCharm.Tier.INGOT, input.getItem(CENTER_SLOT));
        }
        if (matchesAlternatingUpgrade(input, BackpackCharm.Tier.INGOT, Items.RAW_GOLD_BLOCK, Items.DEEPSLATE_LAPIS_ORE)) {
            return new Match(BackpackCharm.Tier.MAGIC, input.getItem(CENTER_SLOT));
        }
        if (matchesAlternatingUpgrade(input, BackpackCharm.Tier.MAGIC, Items.DIAMOND, Items.EMERALD_BLOCK)) {
            return new Match(BackpackCharm.Tier.BEJEWELED, input.getItem(CENTER_SLOT));
        }
        if (matchesWitheredBackpack(input)) {
            return new Match(BackpackCharm.Tier.WITHERED, input.getItem(CENTER_SLOT));
        }
        if (matchesEndlessBackpack(input)) {
            return new Match(BackpackCharm.Tier.ENDLESS, input.getItem(CENTER_SLOT));
        }

        return null;
    }

    private static boolean matchesLeatherBackpack(CraftingInput input) {
        if (!isVanilla(input.getItem(CENTER_SLOT), Items.CHEST)) {
            return false;
        }

        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == CENTER_SLOT) {
                continue;
            }
            if (!isVanilla(input.getItem(i), Items.LEATHER)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesAlternatingUpgrade(
            CraftingInput input,
            BackpackCharm.Tier sourceTier,
            Item cornerItem,
            Item edgeItem
    ) {
        if (!BackpackCharm.isTier(input.getItem(CENTER_SLOT), sourceTier)) {
            return false;
        }

        for (int slot : CORNERS) {
            if (!isVanilla(input.getItem(slot), cornerItem)) {
                return false;
            }
        }
        for (int slot : EDGES) {
            if (!isVanilla(input.getItem(slot), edgeItem)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesWitheredBackpack(CraftingInput input) {
        return BackpackCharm.isTier(input.getItem(CENTER_SLOT), BackpackCharm.Tier.BEJEWELED)
                && isVanilla(input.getItem(1), Items.NETHER_STAR)
                && isVanilla(input.getItem(3), Items.NETHERITE_SCRAP)
                && isVanilla(input.getItem(5), Items.NETHERITE_SCRAP)
                && isVanilla(input.getItem(7), Items.NETHERITE_SCRAP)
                && isVanilla(input.getItem(0), Items.SOUL_SAND)
                && isVanilla(input.getItem(2), Items.SOUL_SAND)
                && isVanilla(input.getItem(6), Items.SOUL_SAND)
                && isVanilla(input.getItem(8), Items.SOUL_SAND);
    }

    private static boolean matchesEndlessBackpack(CraftingInput input) {
        return BackpackCharm.isTier(input.getItem(CENTER_SLOT), BackpackCharm.Tier.WITHERED)
                && isVanilla(input.getItem(1), Items.DRAGON_HEAD)
                && isVanilla(input.getItem(7), Items.DRAGON_EGG)
                && isEnderiteIngot(input.getItem(3))
                && isEnderiteIngot(input.getItem(5))
                && isVanilla(input.getItem(0), Items.END_STONE)
                && isVanilla(input.getItem(2), Items.END_STONE)
                && isVanilla(input.getItem(6), Items.END_STONE)
                && isVanilla(input.getItem(8), Items.END_STONE);
    }

    private static boolean isVanilla(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.getItem() == item;
    }

    private static boolean isEnderiteIngot(ItemStack stack) {
        return !stack.isEmpty() && FakeItems.isSpecificFakeItem(stack, "enderite-ingot");
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return getMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = getMatch(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }

        return BackpackCharm.createTierStack(match.resultTier(), match.sourceBackpack());
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.BACKPACK_UPGRADE_RECIPE;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
