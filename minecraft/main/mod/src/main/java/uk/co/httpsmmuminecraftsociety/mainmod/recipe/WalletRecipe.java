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
import uk.co.httpsmmuminecraftsociety.mainmod.utils.WalletUtils;

public class WalletRecipe implements CraftingRecipe
{
    private final int trashVal;

    public WalletRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    @Override
    public boolean matches(CraftingInput inputs, Level level)
    {
        if (inputs.size() != 2) return false;

        boolean hasRabbitHide = false;
        boolean hasString = false;

        for (ItemStack item : inputs.items()) {
            if (item.isEmpty()) continue;

            if (item.getItem() == Items.RABBIT_HIDE)
                hasRabbitHide = true;
            if (item.getItem() == Items.STRING)
                hasString = true;
        }

        return hasRabbitHide && hasString;
    }

    @Override
    public ItemStack assemble(CraftingInput inputs, HolderLookup.Provider provider)
    {
        return WalletUtils.createWalletStack();
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.WALLET_SERIALIZER;
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

    public static final class Serializer implements RecipeSerializer<WalletRecipe> {

        public static final MapCodec<WalletRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, WalletRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, WalletRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        WalletRecipe::new
                );

        @Override
        public MapCodec<WalletRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, WalletRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
