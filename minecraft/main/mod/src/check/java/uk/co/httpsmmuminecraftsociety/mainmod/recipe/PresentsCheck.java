package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.CraftingInput;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PresentsCheck {
    private PresentsCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        FakeItem paper = new FakeItem("Red Wrapping Paper", "wrapping-paper-red", Rarity.COMMON,
                "common", 64, false, false, "", List.of(), Items.HEART_OF_THE_SEA, List.of(), null);
        FakeItem present = new FakeItem("Red Present", "present-red", Rarity.COMMON,
                "common", 1, false, false, "", List.of(), Items.REPEATING_COMMAND_BLOCK, List.of(), null);
        FakeItems.ID_MAP = Map.of(paper.id(), paper, present.id(), present);

        ItemStack wrapper = paper.createItemStack();

        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Gift sword"));
        List<ItemStack> grid = new ArrayList<>();
        grid.add(wrapper);
        grid.add(named);
        for (int i = 0; i < 7; i++) grid.add(new ItemStack(Items.APPLE));
        PresentRecipe presentRecipe = new PresentRecipe();
        CraftingInput input = CraftingInput.of(3, 3, grid);
        assert presentRecipe.matches(input, null);
        ItemStack gift = presentRecipe.assemble(input);
        assert FakeItems.isSpecificFakeItem(gift, present.id());
        List<ItemStack> contents = gift.get(DataComponents.CONTAINER).itemCopies().toList();
        assert contents.size() == 8;
        assert contents.getFirst().getHoverName().getString().equals("Gift sword");
        assert contents.stream().allMatch(stack -> stack.getCount() == 1);
        assert !presentRecipe.matches(CraftingInput.of(2, 2,
                List.of(wrapper, named, ItemStack.EMPTY, ItemStack.EMPTY)), null);
        System.out.println("Present crafting and contents checks passed.");
    }
}
