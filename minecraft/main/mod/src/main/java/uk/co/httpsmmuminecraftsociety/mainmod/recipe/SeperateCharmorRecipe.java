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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.List;

public class SeperateCharmorRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.ingredientCount() > 1) return false;

        ItemStack stack = recipeInput.items().getFirst();

        if (!stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) return false;

        return CharmorManager.calcUsedCharmSlotCount(stack) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack inputStack = input.items().getFirst();
        List<FakeItem> charms = CharmsManager.getAbilitiesFromItemStack(inputStack);
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
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.SEPERATE_CHARMOR_SERIALIZER;
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
}
