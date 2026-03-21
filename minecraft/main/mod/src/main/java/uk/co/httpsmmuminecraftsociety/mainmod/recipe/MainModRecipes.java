package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven.FakeShapedCraftingRecipe;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven.FakeShapelessCraftingRecipe;

public final class MainModRecipes {
    private MainModRecipes() {}

    public static final RecipeSerializer<CoinConvertRecipe> COIN_CONVERT_SERIALIZER = new CoinConvertRecipe.Serializer();
    public static final RecipeSerializer<CombineCharmorRecipe> COMBINE_CHARMOR_SERIALIZER = new CombineCharmorRecipe.Serializer();
    public static final RecipeSerializer<SeperateCharmorRecipe> SEPERATE_CHARMOR_SERIALIZER = new SeperateCharmorRecipe.Serializer();
    public static final RecipeSerializer<CombineCosmeticRecipe> COMBINE_COSMETIC_SERIALIZER = new CombineCosmeticRecipe.Serializer();
    public static final RecipeSerializer<SeperateCosmeticRecipe> SEPERATE_COSMETIC_SERIALIZER = new SeperateCosmeticRecipe.Serializer();
    public static final RecipeSerializer<NutritionalPasteRecipe> NUTRITIONAL_PASTE_SERIALIZER = new NutritionalPasteRecipe.Serializer();
    public static final RecipeSerializer<FakeShapedCraftingRecipe> FAKE_CRAFTING_SHAPED_SERIALIZER = new FakeShapedCraftingRecipe.Serializer();
    public static final RecipeSerializer<FakeShapelessCraftingRecipe> FAKE_CRAFTING_SHAPELESS_SERIALIZER = new FakeShapelessCraftingRecipe.Serializer();

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "coin_convert"), COIN_CONVERT_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_charmor"), COMBINE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_charmor"), SEPERATE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_cosmetic"), COMBINE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_cosmetic"), SEPERATE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "nutritional_paste"), NUTRITIONAL_PASTE_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fake_crafting_shaped"), FAKE_CRAFTING_SHAPED_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fake_crafting_shapeless"), FAKE_CRAFTING_SHAPELESS_SERIALIZER);
    }
}
