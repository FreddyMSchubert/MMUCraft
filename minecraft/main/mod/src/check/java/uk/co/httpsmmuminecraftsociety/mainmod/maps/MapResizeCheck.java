package uk.co.httpsmmuminecraftsociety.mainmod.maps;

import net.minecraft.SharedConstants;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MapResizeRecipe;

import java.util.List;

public final class MapResizeCheck {
    private MapResizeCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        MapResizeRecipe recipe = new MapResizeRecipe();
        ItemStack map = new ItemStack(Items.MAP);
        for (int expected = 256; expected <= SmallMaps.MAX_SIZE; expected *= 2) {
            CraftingInput input = grid(map, new ItemStack(Items.PAPER));
            assert recipe.matches(input, null) : "Paper should enlarge the map to " + expected;
            map = recipe.assemble(input);
            SmallMaps.processCraftedMap(map, null);
            assert SmallMaps.size(map, null) == expected;
        }
        assert !recipe.matches(grid(map, new ItemStack(Items.PAPER)), null)
                : "Paper must stop at 2048 blocks";

        for (int expected = SmallMaps.MAX_SIZE / 2; expected >= SmallMaps.MIN_SIZE; expected /= 2) {
            CraftingInput input = grid(map, new ItemStack(Items.SHEARS));
            assert recipe.matches(input, null) : "Shears should shrink the map to " + expected;
            map = recipe.assemble(input);
            SmallMaps.processCraftedMap(map, null);
            assert SmallMaps.size(map, null) == expected;
        }
        assert !recipe.matches(grid(map, new ItemStack(Items.SHEARS)), null)
                : "Shears must stop at 16 blocks";
        System.out.println("Map resize checks passed: 16 through 2048 blocks in both directions.");
    }

    private static CraftingInput grid(ItemStack map, ItemStack tool) {
        return CraftingInput.of(2, 1, List.of(map, tool));
    }
}
