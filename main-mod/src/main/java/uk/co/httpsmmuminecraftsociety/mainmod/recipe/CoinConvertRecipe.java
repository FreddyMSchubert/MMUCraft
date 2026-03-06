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
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItems;

public final class CoinConvertRecipe implements CraftingRecipe
{

    private final String fromId;
    private final int fromCount;
    private final String toId;
    private final int toCount;

    public CoinConvertRecipe(String fromId, int fromCount, String toId, int toCount) {
        this.fromId = fromId;
        this.fromCount = fromCount;
        this.toId = toId;
        this.toCount = toCount;
    }

    @Override
    public boolean matches(CraftingInput input, @NotNull Level level) {
        int found = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;

            // reject anything that's not exactly the "from" coin
            if (!isFakeCoin(s, fromId)) return false;
            found++;
        }

        return found == fromCount;
    }

    @Override
    public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.@NotNull Provider registries) {
        ItemStack stack =  FakeItems.MODEL_ID_MAP.get(toId).createItemStack();
        stack.setCount(toCount);
        return stack;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return MainModRecipes.COIN_CONVERT_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.MISC;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        // In 1.21+ categories are registry-backed; use the shared category constants helper.
        return RecipeBookCategories.CRAFTING_MISC;
    }

    private static boolean isFakeCoin(ItemStack stack, String id) {
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return cmd != null && cmd.strings().contains(id);
    }

    public static final class Serializer implements RecipeSerializer<CoinConvertRecipe> {

        public static final MapCodec<CoinConvertRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.NON_EMPTY_STRING.fieldOf("from").forGetter(r -> r.fromId),
                        ExtraCodecs.POSITIVE_INT.fieldOf("from_count").forGetter(r -> r.fromCount),
                        ExtraCodecs.NON_EMPTY_STRING.fieldOf("to").forGetter(r -> r.toId),
                        ExtraCodecs.POSITIVE_INT.fieldOf("to_count").forGetter(r -> r.toCount)
                ).apply(instance, CoinConvertRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CoinConvertRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, r -> r.fromId,
                        ByteBufCodecs.VAR_INT,     r -> r.fromCount,
                        ByteBufCodecs.STRING_UTF8, r -> r.toId,
                        ByteBufCodecs.VAR_INT,     r -> r.toCount,
                        CoinConvertRecipe::new
                );

        @Override
        public MapCodec<CoinConvertRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CoinConvertRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}