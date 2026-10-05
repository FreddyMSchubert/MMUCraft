package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class ToggleArmorAppearanceRecipe extends CustomRecipe {
    private final boolean toggleCharms;

    public ToggleArmorAppearanceRecipe(boolean toggleCharms) {
        this.toggleCharms = toggleCharms;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!FeatureToggles.isEnabled(FeatureToggles.SOARING)) return false;
        if (input.ingredientCount() != 2) return false;
        boolean hasArmor = false;
        boolean hasGlass = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (acceptsArmor(stack) && !hasArmor) {
                hasArmor = true;
            } else if (stack.is(toggleCharms ? Items.GLASS_PANE : Items.GLASS) && !hasGlass) {
                hasGlass = true;
            } else {
                return false;
            }
        }
        return hasArmor && hasGlass;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack armor = input.items().stream()
                .filter(this::acceptsArmor)
                .findFirst().orElseThrow().copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, armor, tag -> {
            String key = toggleCharms ? CharmorManager.CHARMS_HIDDEN_BOOL : CharmorManager.ARMOR_MINIFIED_BOOL;
            boolean current = tag.getBoolean(key).orElse(false);
            tag.putBoolean(key, !current);
        });
        return CharmorManager.updateArmorTooltip(armor);
    }

    private boolean acceptsArmor(ItemStack stack) {
        return toggleCharms ? stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)
                : CharmorManager.canMinifyArmor(stack);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return toggleCharms ? MainModRecipes.TOGGLE_CHARM_VISIBILITY_SERIALIZER
                : MainModRecipes.TOGGLE_ARMOR_MINIFICATION_SERIALIZER;
    }
}
