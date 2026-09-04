package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.TrailParticle;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.WeightedTrailSpec;

import java.util.EnumMap;
import java.util.Map;

public final class SetParticleTrailRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasTarget = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (ParticleTrailData.supports(stack)) {
                if (hasTarget) return false;
                hasTarget = true;
            } else if (TrailParticle.fromItem(stack) == null || FakeItems.getFakeItemFromStack(stack) != null) {
                return false;
            }
        }
        return hasTarget;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack target = input.items().stream().filter(ParticleTrailData::supports).findFirst().orElseThrow();
        Map<TrailParticle, Integer> weights = new EnumMap<>(TrailParticle.class);
        weights.putAll(ParticleTrailData.getTrailSpec(target).weights());
        boolean added = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty() || ParticleTrailData.supports(stack)) continue;
            WeightedTrailSpec.addWeight(weights, TrailParticle.fromItem(stack), 1);
            added = true;
        }
        ItemStack result = target.copyWithCount(1);
        ParticleTrailData.setTrailSpec(result, added ? new WeightedTrailSpec(weights) : WeightedTrailSpec.EMPTY);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        for (int index = 0; index < input.size(); index++) {
            if (input.getItem(index).is(Items.SULFUR_CUBE_BUCKET)) remaining.set(index, new ItemStack(Items.BUCKET));
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.SET_PARTICLE_TRAIL_SERIALIZER;
    }
}
