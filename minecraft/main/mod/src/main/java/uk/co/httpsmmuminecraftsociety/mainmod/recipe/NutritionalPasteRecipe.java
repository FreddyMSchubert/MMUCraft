package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

public class NutritionalPasteRecipe implements CraftingRecipe
{
    private final int trashVal; // dummy field because minecraft forces their recipes data driven. well data drive this important value.

    public NutritionalPasteRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        boolean hasCookie = false;
        boolean hasBeetroot = false;
        boolean hasAnyMushroom = false;
        boolean hasAnyFungi = false;
        boolean hasPumpkin = false;
        boolean hasGoldenNugget = false;
        boolean hasAnyFish = false;
        boolean hasAnyFarmAnimal = false;
        boolean hasBowl = false;

        for (ItemStack stack : input.items()) {
            if (stack.getItem() == Items.COOKIE && !hasCookie)
                hasCookie = true;
            if (stack.getItem() == Items.BEETROOT && !hasBeetroot)
                hasBeetroot = true;
            if (stack.getItem() == Items.PUMPKIN && !hasPumpkin)
                hasPumpkin = true;
            if (stack.getItem() == Items.GOLD_NUGGET && !hasGoldenNugget)
                hasGoldenNugget = true;
            if (stack.getItem() == Items.BOWL && !hasBowl)
                hasBowl = true;
            if (stack.is(ModItemTagProvider.FISHES) && !hasAnyFish)
                hasAnyFish = true;
            if (stack.is(ModItemTagProvider.MUSHROOMS) && !hasAnyMushroom)
                hasAnyMushroom = true;
            if (stack.is(ModItemTagProvider.FUNGI) && !hasAnyFungi)
                hasAnyFungi = true;
            if (stack.is(ModItemTagProvider.FARM_ANIMAL_MEATS_RAW) && !hasAnyFarmAnimal)
                hasAnyFarmAnimal = true;
        }

        return hasCookie && hasBeetroot && hasAnyMushroom && hasAnyFungi && hasPumpkin && hasGoldenNugget && hasAnyFish && hasAnyFarmAnimal && hasBowl;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider)
    {
        return FakeItems.CONSUMABLE_MODEL_ID_MAP.get(1).createItemStack();
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.NUTRITIONAL_PASTE_SERIALIZER;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static final class Serializer implements RecipeSerializer<NutritionalPasteRecipe> {

        public static final MapCodec<NutritionalPasteRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, NutritionalPasteRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, NutritionalPasteRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        NutritionalPasteRecipe::new
                );

        @Override
        public MapCodec<NutritionalPasteRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, NutritionalPasteRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
