package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;

import java.util.List;
import java.util.Optional;

public final class EnderiteSmithingRecipe extends SimpleSmithingRecipe {
    private static final String TEMPLATE_FAKE_ITEM_ID = "enderite-upgrade-smithing-template";
    private static final String INGOT_FAKE_ITEM_ID = "enderite-ingot";

    private static final Ingredient TEMPLATE_INGREDIENT = Ingredient.of(Items.COMMAND_BLOCK);
    private static final Ingredient BASE_INGREDIENT = Ingredient.of(
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD,
            Items.NETHERITE_SPEAR,
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_AXE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HOE
    );
    private static final Ingredient ADDITION_INGREDIENT = Ingredient.of(Items.COMMAND_BLOCK);

    public EnderiteSmithingRecipe() {
        super(new Recipe.CommonInfo(false));
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return isTemplate(input.template())
                && isUpgradeableBase(input.base())
                && isAddition(input.addition());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        if (!isTemplate(input.template()) || !isUpgradeableBase(input.base()) || !isAddition(input.addition())) {
            return ItemStack.EMPTY;
        }

        ItemStack result = input.base().copy();
        result.setCount(1);

        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(CharmorManager.ENDERITE_MARKER_BOOL, true);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        CustomModelData modelData = result.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                modelData.floats(), List.of(true), modelData.strings(), modelData.colors()
        ));

        Equippable equippable = result.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            result.set(
                    DataComponents.EQUIPPABLE,
                    EquippableCharmItemFeature.createEquippableSettings("enderite", equippable.slot())
            );
        }

        CharmorManager.updateArmorTooltip(result);
        return result;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(TEMPLATE_INGREDIENT);
    }

    @Override
    public Ingredient baseIngredient() {
        return BASE_INGREDIENT;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(ADDITION_INGREDIENT);
    }

    @Override
    public RecipeSerializer<EnderiteSmithingRecipe> getSerializer() {
        return MainModRecipes.ENDERITE_UPGRADE_SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(
                templateIngredient(),
                Optional.of(baseIngredient()),
                additionIngredient()
        ));
    }

    private static boolean isTemplate(ItemStack stack) {
        return FakeItems.isSpecificFakeItem(stack, TEMPLATE_FAKE_ITEM_ID);
    }

    private static boolean isAddition(ItemStack stack) {
        return FakeItems.isSpecificFakeItem(stack, INGOT_FAKE_ITEM_ID);
    }

    private static boolean isUpgradeableBase(ItemStack stack) {
        return BASE_INGREDIENT.test(stack) && !CharmorManager.isEnderite(stack);
    }
}
