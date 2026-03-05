package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class MainModRecipes {
    private MainModRecipes() {}

    public static final String MODID = "mainmod";

    public static final RecipeSerializer<CoinConvertRecipe> COIN_CONVERT_SERIALIZER = new CoinConvertRecipe.Serializer();

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(MODID, "coin_convert");
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, COIN_CONVERT_SERIALIZER);
    }
}