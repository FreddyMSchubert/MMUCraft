package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
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
        assert !gift.has(DataComponents.CONTAINER);
        List<ItemStack> contents = contents(gift);
        assert contents.size() == 8;
        assert contents.getFirst().getHoverName().getString().equals("Gift sword");
        assert contents.stream().allMatch(stack -> stack.getCount() == 1);
        CraftingInput small = CraftingInput.of(2, 2,
                List.of(named, ItemStack.EMPTY, ItemStack.EMPTY, wrapper));
        assert presentRecipe.matches(small, null);
        assert contents(presentRecipe.assemble(small)).getFirst().getHoverName().getString().equals("Gift sword");
        CraftingInput ordered = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.APPLE), ItemStack.EMPTY, named,
                ItemStack.EMPTY, wrapper, ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND), ItemStack.EMPTY, new ItemStack(Items.EMERALD)));
        List<ItemStack> orderedContents = contents(presentRecipe.assemble(ordered));
        assert orderedContents.size() == 4;
        assert orderedContents.get(0).is(Items.APPLE);
        assert orderedContents.get(1).getHoverName().getString().equals("Gift sword");
        assert orderedContents.get(2).is(Items.DIAMOND);
        assert orderedContents.get(3).is(Items.EMERALD);
        System.out.println("Present crafting and contents checks passed.");
    }

    private static List<ItemStack> contents(ItemStack present) {
        List<ItemStack> items = new ArrayList<>();
        CompoundTag tag = present.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (Tag entry : tag.getListOrEmpty("mainmod_present_contents_v1")) {
            if (entry instanceof CompoundTag saved) saved.read("item", ItemStack.CODEC).ifPresent(items::add);
        }
        return items;
    }
}
