package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.List;

public class CombineCosmeticRecipe implements CraftingRecipe
{
    private final int trashVal; // dummy field because minecraft forces their recipes data driven. well data drive this important value.

    public CombineCosmeticRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    private record craftingInfo(boolean craftable, ItemStack armor, ItemStack cosmetic) {}

    private craftingInfo getCraftingInfo(CraftingInput input)
    {
        // must have 1x armor item with a free slot
        ItemStack armor = null;
        // and 1x cosmetic
        ItemStack cosmetic = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);

            MainMod.LOGGER.info("Checking if the armor item is in COSMETIC_COMB...: " + stack.getItem().getName());
            if (stack.getItem().getDefaultInstance().is(ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS)) {
                MainMod.LOGGER.info("cmd strings: " + cmd.strings());
                if (armor != null || !cmd.strings().isEmpty()) continue;
                armor = stack;
                continue;
            }

            if (!cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("cosmetic-hat-") && stack.getItem().equals(Items.CARVED_PUMPKIN)) {
                if (cosmetic != null) continue;
                cosmetic = stack;
                continue;
            }
        }

        MainMod.LOGGER.info("armor :" + armor + " cosmetic: " + cosmetic);
        return new craftingInfo(armor != null && cosmetic != null, armor, cosmetic);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        craftingInfo cinfo = getCraftingInfo(input);
        MainMod.LOGGER.info("Iscraftable: " + cinfo.craftable);
        return cinfo.craftable && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider)
    {
        craftingInfo cinfo = getCraftingInfo(input);

        ItemStack pumpkin = CosmeticsManager.helmetToPumpkinReplica(cinfo.armor);
        pumpkin.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(cinfo.cosmetic.get(DataComponents.CUSTOM_MODEL_DATA).getString(0)), List.of()));
        DyedItemColor cosmeticColor = cinfo.cosmetic.get(DataComponents.DYED_COLOR);
        if (cosmeticColor != null) {
            pumpkin.set(DataComponents.DYED_COLOR, cosmeticColor);
        }

        return pumpkin;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.COMBINE_CHARMOR_SERIALIZER;
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

    public static final class Serializer implements RecipeSerializer<CombineCosmeticRecipe> {

        public static final MapCodec<CombineCosmeticRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, CombineCosmeticRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CombineCosmeticRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        CombineCosmeticRecipe::new
                );

        @Override
        public MapCodec<CombineCosmeticRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CombineCosmeticRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
