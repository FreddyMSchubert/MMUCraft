package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public class SeperateCosmeticRecipe implements CraftingRecipe
{
    private final int trashVal; // dummy field because minecraft forces their recipes data driven. well data drive this important value.

    public SeperateCosmeticRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.ingredientCount() > 1) return false;

        ItemStack stack = recipeInput.items().getFirst();
        CosmeticsManager.CosmeticsInfo cinfo = CosmeticsManager.determineCosmeticType(stack);
        return cinfo.isCosmetic() && cinfo.isHelmet();
    }

    @Override
    public ItemStack assemble(CraftingInput recipeInput, HolderLookup.Provider provider)
    {
        ItemStack inputStack = recipeInput.items().getFirst();

        String cosmeticModel = inputStack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).getString(0);
        if (cosmeticModel == null) {
            return ItemStack.EMPTY;
        }

        ItemStack cosmetic = FakeItems.MODEL_ID_MAP.get(cosmeticModel).createItemStack();

        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (dyedColor != null) {
            cosmetic.set(DataComponents.DYED_COLOR, dyedColor);
        }
        CompoundTag nbt = inputStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (nbt.contains(CosmeticsManager.COLOR_CYCLING_BOOLEAN)) {
            CompoundTag cosmeticNbt = cosmetic.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            cosmeticNbt.putBoolean(CosmeticsManager.COLOR_CYCLING_BOOLEAN, nbt.getBooleanOr(CosmeticsManager.COLOR_CYCLING_BOOLEAN, false));
            cosmetic.set(DataComponents.CUSTOM_DATA, CustomData.of(cosmeticNbt));
        }

        return cosmetic;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput craftingInput)
    {
        NonNullList<ItemStack> list = NonNullList.withSize(craftingInput.ingredientCount(), ItemStack.EMPTY);
        for (int i = 0; i < craftingInput.ingredientCount(); i++) {
            ItemStack stack = craftingInput.items().get(i).copy();
            if (!stack.isEmpty()) {
                list.set(i, CosmeticsManager.pumpkinReplicaToHelmet(stack));
                continue;
            }
            list.set(i, stack);
        }
        return list;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.SEPERATE_COSMETIC_SERIALIZER;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
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

    public static final class Serializer implements RecipeSerializer<SeperateCosmeticRecipe> {

        public static final MapCodec<SeperateCosmeticRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, SeperateCosmeticRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SeperateCosmeticRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        SeperateCosmeticRecipe::new
                );

        @Override
        public MapCodec<SeperateCosmeticRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SeperateCosmeticRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
