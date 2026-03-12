package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItems;

public class RemoveJebbonatorRecipe implements CraftingRecipe {
    private final int trashVal;

    public RemoveJebbonatorRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    // only matches non-helmet-pumpkins, so helmet is always removed before jebbonator
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 1) return false;
        ItemStack stack = input.items().getFirst();
        CosmeticsManager.CosmeticsInfo cinfo = CosmeticsManager.determineCosmeticType(stack);
        return (cinfo.isCosmetic() && cinfo.isColorCycling() && !cinfo.isHelmet());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        return FakeItems.MODEL_ID_MAP.get("jebbonator5k").createItemStack();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.ingredientCount(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i ++) {
            ItemStack stack = input.getItem(i).copy();
            if (stack.isEmpty()) continue;
            CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            nbt.putBoolean(CosmeticsManager.COLOR_CYCLING_BOOLEAN, false);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            list.set(i, stack);
        }
        return list;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return MainModRecipes.REMOVE_JEBBONATOR_SERIALIZER;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static final class Serializer implements RecipeSerializer<RemoveJebbonatorRecipe> {
        public static final MapCodec<RemoveJebbonatorRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, RemoveJebbonatorRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveJebbonatorRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        RemoveJebbonatorRecipe::new
                );

        @Override
        public MapCodec<RemoveJebbonatorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RemoveJebbonatorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
