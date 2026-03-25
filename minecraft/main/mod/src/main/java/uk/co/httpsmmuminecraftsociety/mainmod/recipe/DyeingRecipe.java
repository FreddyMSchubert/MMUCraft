package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.DyedItemColor;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.DyeableItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;

public final class DyeingRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack target = ItemStack.EMPTY;
        int dyeCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof DyeItem) {
                dyeCount++;
                continue;
            }

            if (!target.isEmpty()) {
                return false; // more than one non-dye target
            }

            if (!isFakeItemDyeable(stack)) {
                return false;
            }

            target = stack;
        }

        return !target.isEmpty() && dyeCount > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack target = ItemStack.EMPTY;
        List<DyeColor> dyes = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            DyeColor dye = stack.get(DataComponents.DYE);
            if (dye != null) {
                dyes.add(dye);
                continue;
            }

            if (target.isEmpty() && isFakeItemDyeable(stack)) {
                target = stack;
                continue;
            }

            return ItemStack.EMPTY;
        }

        if (target.isEmpty() || dyes.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return DyedItemColor.applyDyes(target, dyes);
    }

    private static boolean isFakeItemDyeable(ItemStack stack) {
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) return false;
        FakeItem fitem = FakeItems.ID_MAP.get(cmd.strings().getFirst());
        return fitem.getFeature(DyeableItemFeature.class) != null;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.DYEING_SERIALIZER;
    }
}