package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class MainModRecipes {
    private MainModRecipes() {}

    public static final String MODID = "mainmod";

    public static final RecipeSerializer<CoinConvertRecipe> COIN_CONVERT_SERIALIZER = new CoinConvertRecipe.Serializer();
    public static final RecipeSerializer<CombineCharmorRecipe> COMBINE_CHARMOR_SERIALIZER = new CombineCharmorRecipe.Serializer();
    public static final RecipeSerializer<SeperateCharmorRecipe> SEPERATE_CHARMOR_SERIALIZER = new SeperateCharmorRecipe.Serializer();

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MODID, "coin_convert"), COIN_CONVERT_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MODID, "combine_charmor"), COMBINE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MODID, "seperate_charmor"), SEPERATE_CHARMOR_SERIALIZER);
    }
}
