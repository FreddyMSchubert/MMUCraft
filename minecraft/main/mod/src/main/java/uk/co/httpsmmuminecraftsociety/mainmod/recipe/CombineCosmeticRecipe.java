package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.ArrayList;
import java.util.List;

public class CombineCosmeticRecipe extends CustomRecipe
{
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

            if (stack.getItem().getDefaultInstance().is(ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS)) {
                if (armor != null || !cmd.strings().isEmpty()) continue;
                armor = stack;
                continue;
            }

            if (!cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("cosmetic-")
                    && stack.is(Items.CARVED_PUMPKIN) && !CosmeticsManager.determineCosmeticType(stack).isHelmet()) {
                if (cosmetic != null) continue;
                cosmetic = stack;
                continue;
            }
        }

        return new craftingInfo(armor != null && cosmetic != null, armor, cosmetic);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        craftingInfo cinfo = getCraftingInfo(input);
        return cinfo.craftable && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        craftingInfo cinfo = getCraftingInfo(input);

        ItemStack pumpkin = CosmeticsManager.helmetToPumpkinReplica(cinfo.armor);
        List<Component> lore = new ArrayList<>(pumpkin.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.add(Component.literal("Cosmetic: ").append(cinfo.cosmetic.getHoverName()));
        lore.addAll(cinfo.armor.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.addAll(cinfo.cosmetic.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        pumpkin.set(DataComponents.LORE, new ItemLore(lore.subList(0, Math.min(lore.size(), ItemLore.MAX_LINES))));
        pumpkin.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                cinfo.armor.copyWithCount(1), cinfo.cosmetic.copyWithCount(1))));
        pumpkin.set(DataComponents.TOOLTIP_DISPLAY,
                pumpkin.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                        .withHidden(DataComponents.CONTAINER, true));
        CustomModelData armorModelData = cinfo.armor.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        pumpkin.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                armorModelData.floats(), armorModelData.flags(),
                List.of(cinfo.cosmetic.get(DataComponents.CUSTOM_MODEL_DATA).getString(0)), armorModelData.colors()
        ));
        DyedItemColor cosmeticColor = cinfo.cosmetic.get(DataComponents.DYED_COLOR);
        if (cosmeticColor != null) {
            pumpkin.set(DataComponents.DYED_COLOR, cosmeticColor);
        } else {
            pumpkin.remove(DataComponents.DYED_COLOR);
        }
        CompoundTag newNbt = pumpkin.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag cosmeticNbt = cinfo.cosmetic.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (cosmeticNbt.contains(CosmeticsManager.COLOR_CYCLING_BOOLEAN)) {
            newNbt.putBoolean(CosmeticsManager.COLOR_CYCLING_BOOLEAN,
                    cosmeticNbt.getBooleanOr(CosmeticsManager.COLOR_CYCLING_BOOLEAN, false));
            pumpkin.set(DataComponents.CUSTOM_DATA, CustomData.of(newNbt));
        }

        return pumpkin;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.COMBINE_COSMETIC_SERIALIZER;
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
