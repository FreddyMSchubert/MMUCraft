package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CosmeticsManager;

public class ApplyJebbonatorRecipe implements CraftingRecipe {
    private final int trashVal;

    public ApplyJebbonatorRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    private record CraftingInfo(boolean craftable, ItemStack target) {}

    private CraftingInfo getCraftingInfo(CraftingInput input) {
        ItemStack target = null;
        ItemStack jebbonator = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            CosmeticsManager.CosmeticsInfo cinfo = CosmeticsManager.determineCosmeticType(stack);
            if (cinfo.isCosmetic() && cinfo.isDyeable() && !cinfo.isColorCycling()) {
                if (target != null) return new CraftingInfo(false, ItemStack.EMPTY);
                target = stack;
                continue;
            }

            CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
            if (!cmd.strings().isEmpty() && "jebbonator5k".equals(cmd.strings().getFirst())) {
                if (jebbonator != null) return new CraftingInfo(false, ItemStack.EMPTY);
                jebbonator = stack;
                continue;
            }

            return new CraftingInfo(false, ItemStack.EMPTY);
        }

        return new CraftingInfo(jebbonator != null && target != null, target);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        CraftingInfo info = getCraftingInfo(input);
        return info.craftable() && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        CraftingInfo info = getCraftingInfo(input);
        if (!info.craftable()) return ItemStack.EMPTY;

        ItemStack result = info.target().copy();
        CompoundTag nbt = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putBoolean(CosmeticsManager.COLOR_CYCLING_BOOLEAN, true);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return result;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return MainModRecipes.APPLY_JEBBONATOR_SERIALIZER;
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

    public static final class Serializer implements RecipeSerializer<ApplyJebbonatorRecipe> {
        public static final MapCodec<ApplyJebbonatorRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, ApplyJebbonatorRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyJebbonatorRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        ApplyJebbonatorRecipe::new
                );

        @Override
        public MapCodec<ApplyJebbonatorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ApplyJebbonatorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
