package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public final class MainModRecipes {
    public static final RecipeSerializer<CombineCharmorRecipe> COMBINE_CHARMOR_SERIALIZER = unit(new CombineCharmorRecipe());
    public static final RecipeSerializer<CharmorVisualResetRecipe> CHARMOR_VISUAL_RESET_SERIALIZER = unit(new CharmorVisualResetRecipe());
    public static final RecipeSerializer<SeperateCharmorRecipe> SEPERATE_CHARMOR_SERIALIZER = unit(new SeperateCharmorRecipe());
    public static final RecipeSerializer<CombineCosmeticRecipe> COMBINE_COSMETIC_SERIALIZER = unit(new CombineCosmeticRecipe());
    public static final RecipeSerializer<SeperateCosmeticRecipe> SEPERATE_COSMETIC_SERIALIZER = unit(new SeperateCosmeticRecipe());
    public static final RecipeSerializer<ExtractXPFromPhialRecipe> EXTRACT_XP_FROM_PHIAL_SERIALIZER = unit(new ExtractXPFromPhialRecipe());
    public static final RecipeSerializer<WalletInsertRecipe> WALLET_INSERT_RECIPE = unit(new WalletInsertRecipe());
    public static final RecipeSerializer<WalletExtractRecipe> WALLET_EXTRACT_RECIPE = unit(new WalletExtractRecipe());
    public static final RecipeSerializer<DyeingRecipe> DYEING_SERIALIZER = unit(new DyeingRecipe());
    public static final RecipeSerializer<BackpackUpgradeRecipe> BACKPACK_UPGRADE_RECIPE = unit(new BackpackUpgradeRecipe());
    public static final RecipeSerializer<SetBowTrailRecipe> SET_BOW_TRAIL_SERIALIZER = unit(new SetBowTrailRecipe());
    public static final RecipeSerializer<EnderiteSmithingRecipe> ENDERITE_UPGRADE_SERIALIZER = unit(new EnderiteSmithingRecipe());
    public static final RecipeSerializer<FishCookingRecipe> FISH_COOKING_SERIALIZER = unit(new FishCookingRecipe());

    public static final RecipeSerializer<FakeShapedCraftingRecipe> FAKE_CRAFTING_SHAPED_SERIALIZER = codecBacked(FakeShapedCraftingRecipe.CODEC);
    public static final RecipeSerializer<FakeShapelessCraftingRecipe> FAKE_CRAFTING_SHAPELESS_SERIALIZER = codecBacked(FakeShapelessCraftingRecipe.CODEC);

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_charmor"), COMBINE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charmor_visual_reset"), CHARMOR_VISUAL_RESET_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_charmor"), SEPERATE_CHARMOR_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "backpack_upgrade"), BACKPACK_UPGRADE_RECIPE);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "combine_cosmetic"), COMBINE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "seperate_cosmetic"), SEPERATE_COSMETIC_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "extract_xp_from_phial"), EXTRACT_XP_FROM_PHIAL_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "wallet_insert"), WALLET_INSERT_RECIPE);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "wallet_extract"), WALLET_EXTRACT_RECIPE);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "dyeing"), DYEING_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "bow_trail"), SET_BOW_TRAIL_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "enderite_upgrade"), ENDERITE_UPGRADE_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fish_cooking"), FISH_COOKING_SERIALIZER);

        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fake_crafting_shaped"), FAKE_CRAFTING_SHAPED_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fake_crafting_shapeless"), FAKE_CRAFTING_SHAPELESS_SERIALIZER);
    }

    private static <T extends Recipe<?>> RecipeSerializer<T> unit(T recipe) {
        return new RecipeSerializer<>(
                MapCodec.unit(recipe),
                StreamCodec.unit(recipe)
        );
    }
    public static <T extends Recipe<?>> RecipeSerializer<T> codecBacked(MapCodec<T> codec) {
        return new RecipeSerializer<>(
                codec,
                ByteBufCodecs.fromCodecWithRegistries(codec.codec())
        );
    }
}
