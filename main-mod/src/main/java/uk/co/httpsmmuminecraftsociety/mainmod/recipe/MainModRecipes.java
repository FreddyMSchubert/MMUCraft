package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public final class MainModRecipes {
    private MainModRecipes() {}

    public static final RecipeSerializer<CoinConvertRecipe> COIN_CONVERT_SERIALIZER = new CoinConvertRecipe.Serializer();
    public static final RecipeSerializer<CombineCharmorRecipe> COMBINE_CHARMOR_SERIALIZER = new CombineCharmorRecipe.Serializer();
    public static final RecipeSerializer<SeperateCharmorRecipe> SEPERATE_CHARMOR_SERIALIZER = new SeperateCharmorRecipe.Serializer();
    public static final RecipeSerializer<CombineCosmeticRecipe> COMBINE_COSMETIC_SERIALIZER = new CombineCosmeticRecipe.Serializer();
    public static final RecipeSerializer<SeperateCosmeticRecipe> SEPERATE_COSMETIC_SERIALIZER = new SeperateCosmeticRecipe.Serializer();
    public static final RecipeSerializer<ApplyJebbonatorRecipe> APPLY_JEBBONATOR_SERIALIZER = new ApplyJebbonatorRecipe.Serializer();
    public static final RecipeSerializer<RemoveJebbonatorRecipe> REMOVE_JEBBONATOR_SERIALIZER = new RemoveJebbonatorRecipe.Serializer();

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "coin_convert"), COIN_CONVERT_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_charmor"), COMBINE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_charmor"), SEPERATE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_cosmetic"), COMBINE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_cosmetic"), SEPERATE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "apply_jebbonator"), APPLY_JEBBONATOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "remove_jebbonator"), REMOVE_JEBBONATOR_SERIALIZER);
    }
}
