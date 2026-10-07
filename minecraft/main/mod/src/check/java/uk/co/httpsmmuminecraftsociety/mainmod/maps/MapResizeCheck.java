package uk.co.httpsmmuminecraftsociety.mainmod.maps;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MapResizeRecipe;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureToggle;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;

public final class MapResizeCheck {
    private MapResizeCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        ItemStack ordinaryMap = new ItemStack(Items.FILLED_MAP);
        assert MapInvisibility.target(ordinaryMap) == null : "Ordinary maps must not be treated as air-tagged maps";
        CustomData.update(DataComponents.CUSTOM_DATA, ordinaryMap,
                tag -> tag.putString("structure", "dnt_illager_hideout"));
        assert MapInvisibility.target(ordinaryMap) == null : "Dungeons and Taverns charts must retain their use action";
        assert !SmallMaps.isMap(ordinaryMap) : "Charts without map data must not be resized or relabeled";
        assert !new MapResizeRecipe().matches(grid(ordinaryMap, new ItemStack(Items.PAPER)), null)
                : "Dungeons and Taverns charts must not match the map resize recipe";
        ordinaryMap.set(DataComponents.MAP_ID, new MapId(0));
        assert SmallMaps.isMap(ordinaryMap) : "Filled maps with map data must remain resizable";

        ItemStack airMap = new ItemStack(Items.MAP);
        MapInvisibility.setTarget(airMap, Blocks.AIR);
        assert MapInvisibility.target(airMap) == Blocks.AIR : "Explicitly tagged blocks must still resolve";
        SmallMaps.refreshTooltipFromStoredSize(airMap);
        assert airMap.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
                .noneMatch(line -> line.getString().startsWith("Invisible block:"))
                : "Air must not produce an invisible-block tooltip";

        MapResizeRecipe recipe = new MapResizeRecipe();
        ItemStack map = new ItemStack(Items.MAP);
        assert !recipe.matches(grid(map, new ItemStack(Items.SHEARS)), null);
        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("imaginative").setEnabled(true))
                .build());
        assert recipe.matches(grid(map, new ItemStack(Items.SHEARS)), null);
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
