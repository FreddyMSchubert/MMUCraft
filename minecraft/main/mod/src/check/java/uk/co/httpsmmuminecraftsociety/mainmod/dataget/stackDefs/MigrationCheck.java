package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.BrewingInput;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.LuckBrewingRecipe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MigrationCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            var json = JsonParser.parseString(Files.readString(Path.of(args[0],
                    "src/main/resources/data/mainmod/recipe/brewing/" + id + "_luck.json")));
            var recipe = LuckBrewingRecipe.SERIALIZER.codec().codec().parse(ops, json).getOrThrow();
            ItemStack awkward = PotionContents.createItemStack(item, Potions.AWKWARD);
            ItemStack clover = new ItemStack(Items.HEART_OF_THE_SEA);
            clover.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of("4-leaf-clover"), List.of()));
            var input = new BrewingInput(awkward, clover);
            assert recipe.matches(input);
            ItemStack output = recipe.assemble(input);
            assert output.is(item) && output.get(DataComponents.POTION_CONTENTS).is(Potions.LUCK);
            assert !recipe.matches(new BrewingInput(awkward, new ItemStack(Items.HEART_OF_THE_SEA)));
            assert !recipe.matches(new BrewingInput(PotionContents.createItemStack(item, Potions.WATER), clover));
            clover.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of("3-leaf-clover"), List.of()));
            assert !recipe.matches(input);
        }
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        var patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Keep"))
                .remove(DataComponents.MAX_DAMAGE).build();
        assert !StackComponentPatchUtil.matches(stack, patch);
        StackComponentPatchUtil.apply(stack, patch);
        assert StackComponentPatchUtil.matches(stack, patch);
        assert !stack.has(DataComponents.MAX_DAMAGE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        assert !StackComponentPatchUtil.matches(stack, patch);
        System.out.println("Migration checks passed: brewing identity, containers, and component patches.");
    }
}
