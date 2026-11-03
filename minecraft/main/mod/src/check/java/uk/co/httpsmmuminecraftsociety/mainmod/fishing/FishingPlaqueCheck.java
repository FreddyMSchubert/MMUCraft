package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.FishingPlaqueRecipe;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.UncraftFishingPlaqueRecipe;

import java.util.List;
import java.util.Map;

public final class FishingPlaqueCheck {
    private FishingPlaqueCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        FakeItem plaqueItem = new FakeItem("Oak Fishing Plaque", "deco-fishing-plaque-oak",
                net.minecraft.world.item.Rarity.COMMON, "common", 64, false, false, "",
                List.of(), Items.HEART_OF_THE_SEA, List.of());
        FakeItems.ID_MAP = Map.of(plaqueItem.id(), plaqueItem);
        ItemStack plaque = plaqueItem.createItemStack();
        ItemStack fish = new ItemStack(Items.COD);
        CustomData.update(DataComponents.CUSTOM_DATA, fish,
                tag -> {
                    tag.putDouble("mainmod_fish_length_cm", 72.4);
                    tag.putInt("mainmod_fish_rarity", 3);
                    tag.putString(FishingCatches.CATCHER_UUID_TAG, "123e4567-e89b-12d3-a456-426614174000");
                });
        fish.set(DataComponents.CUSTOM_NAME, Component.literal("Prize Cod"));
        fish.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Caught by: Alex"))));
        FishingPlaqueRecipe recipe = new FishingPlaqueRecipe();
        CraftingInput input = CraftingInput.of(2, 1, List.of(plaque, fish));
        assert recipe.matches(input, null);
        ItemStack filled = recipe.assemble(input);
        assert FishingPlaques.fish(filled).getItem() == Items.COD;
        assert FishingPlaques.fish(filled).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getDoubleOr("mainmod_fish_length_cm", 0.0) == 72.4;
        assert !recipe.matches(CraftingInput.of(2, 1, List.of(filled, fish)), null)
                : "Crafting a filled plaque must not consume its previous fish";
        UncraftFishingPlaqueRecipe uncraft = new UncraftFishingPlaqueRecipe();
        CraftingInput uncraftInput = CraftingInput.of(3, 1, List.of(ItemStack.EMPTY, filled, ItemStack.EMPTY));
        assert uncraft.matches(uncraftInput, null);
        ItemStack recovered = uncraft.assemble(uncraftInput);
        assert recovered.getItem() == fish.getItem() && recovered.getCount() == fish.getCount();
        assert recovered.getComponents().equals(fish.getComponents()) : "Uncrafting must return the exact fish data";
        ItemStack emptyPlaque = uncraft.getRemainingItems(uncraftInput).getFirst();
        assert FishingPlaques.isPlaque(emptyPlaque) && FishingPlaques.fish(emptyPlaque).isEmpty();
        assert emptyPlaque.getHoverName().getString().equals(plaque.getHoverName().getString());
        assert !uncraft.matches(CraftingInput.of(1, 1, List.of(emptyPlaque)), null);
        assert FishingPlaques.fish(FishingPlaques.withFish(filled, ItemStack.EMPTY)).isEmpty();
        System.out.println("Fishing plaque crafting, uncrafting, and fish persistence checks passed.");
    }
}
