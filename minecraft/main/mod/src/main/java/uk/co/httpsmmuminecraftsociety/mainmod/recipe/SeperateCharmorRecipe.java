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
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CharmFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.List;

public class SeperateCharmorRecipe implements CraftingRecipe
{
    private final int trashVal; // dummy field because minecraft forces their recipes data driven. well data drive this important value.

    public SeperateCharmorRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.ingredientCount() > 1) return false;

        ItemStack stack = recipeInput.items().getFirst();

        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return false;

        return CharmorManager.calcUsedCharmSlotCount(stack) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput recipeInput, HolderLookup.Provider provider)
    {
        ItemStack inputStack = recipeInput.items().getFirst();
        List<CharmFakeItem> charms = CharmsManager.getAbilitiesFromItemStack(inputStack);
        return charms.getLast().createItemStack();
    }

    private static ItemStack removeLastCharmFromStack(ItemStack stack) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!nbt.contains(CharmsManager.CHARM_ABILITES_COMPOUND_ID)) return stack;

        int[] charmSlots = nbt.getIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID).get();
        if (charmSlots.length == 0) return stack;
        int[] newCharmSlots = new int[charmSlots.length - 1];
        System.arraycopy(charmSlots, 0, newCharmSlots, 0, newCharmSlots.length);

        nbt.putIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID, newCharmSlots);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        return stack;
    }
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput craftingInput)
    {
        NonNullList<ItemStack> list = NonNullList.withSize(craftingInput.ingredientCount(), ItemStack.EMPTY);
        for (int i = 0; i < craftingInput.ingredientCount(); i++) {
            ItemStack stack = craftingInput.items().get(i).copy();
            if (!stack.isEmpty()) {
                stack = removeLastCharmFromStack(stack);
                stack = CharmorManager.updateArmorTooltip(stack);
            }
            list.set(i, stack);
        }
        return list;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.SEPERATE_CHARMOR_SERIALIZER;
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

    public static final class Serializer implements RecipeSerializer<SeperateCharmorRecipe> {

        public static final MapCodec<SeperateCharmorRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, SeperateCharmorRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SeperateCharmorRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        SeperateCharmorRecipe::new
                );

        @Override
        public MapCodec<SeperateCharmorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SeperateCharmorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
