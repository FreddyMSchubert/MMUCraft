package uk.co.httpsmmuminecraftsociety.mainmod;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.*;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.SetParticleTrailRecipe;

import java.util.List;
import java.util.Map;

public final class ParticleTrailsCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(components -> components.apply());
        SetParticleTrailRecipe recipe = new SetParticleTrailRecipe();
        for (var item : List.of(Items.BOW, Items.ELYTRA)) {
            ItemStack original = new ItemStack(item);
            original.setDamageValue(12);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
            original.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Keep this lore"))));
            CompoundTag data = new CompoundTag();
            data.putString("unrelated", "keep");
            original.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            ItemStack target = original.copy();
            ListTag legacy = new ListTag();
            for (String dye : List.of("red", "red", "blue")) {
                CompoundTag entry = new CompoundTag();
                entry.putString("dye", dye);
                entry.putInt("weight", 1);
                legacy.add(entry);
            }
            data.put("arrow_trail", legacy);
            target.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var input = grid(target, new ItemStack(Items.DYE.red(), 64), new ItemStack(Items.BLAZE_POWDER));
            assert recipe.matches(input, null);
            ItemStack result = recipe.assemble(input);
            WeightedTrailSpec spec = ParticleTrailData.getTrailSpec(result);
            assert spec.weights().equals(Map.of(TrailParticle.RED, 3, TrailParticle.BLUE, 1, TrailParticle.FLAME, 1));
            assert ParticleTrailData.getTrailSpec(target).totalWeight(true) == 3;
            assert input.getItem(1).getCount() == 64;
            assert spec.totalWeight(true) == 5 && spec.totalWeight(false) == 4;
            assert spec.pick(fixed(0), true) == TrailParticle.BLUE;
            assert spec.pick(fixed(0.2), true) == TrailParticle.RED;
            assert spec.pick(fixed(0.8), true) == TrailParticle.FLAME;
            assert spec.pick(fixed(0.25), false) == TrailParticle.RED;
            assert spec.pick(fixed(0.999999), false) == TrailParticle.RED;
            assert spec.pick(fixed(0.8), true) == TrailParticle.FLAME;
            assert result.get(DataComponents.LORE).lines().stream().anyMatch(line -> line.getString().equals("Red dust (60%)"));
            ItemLore lore = result.get(DataComponents.LORE);
            ParticleTrailData.updateTooltip(result);
            assert result.get(DataComponents.LORE).equals(lore);
            ItemStack cleared = recipe.assemble(grid(result));
            assert ItemStack.isSameItemSameComponents(original, cleared);
            for (var particle : TrailParticle.values()) {
                ItemStack crafted = recipe.assemble(grid(original, new ItemStack(particle.ingredient)));
                assert ParticleTrailData.getTrailSpec(crafted).weights().equals(Map.of(particle, 1)) : particle;
            }
        }
        assert !recipe.matches(grid(new ItemStack(Items.BOW), new ItemStack(Items.ELYTRA)), null);
        assert !recipe.matches(grid(new ItemStack(Items.BOW), new ItemStack(Items.DIRT)), null);
        assert !recipe.matches(grid(new ItemStack(Items.BLAZE_POWDER)), null);
        assert recipe.assemble(grid(new ItemStack(Items.BOW), new ItemStack(Items.DIRT))).isEmpty();
        var memberOnly = new WeightedTrailSpec(Map.of(TrailParticle.SONIC_BOOM, 1));
        assert memberOnly.pick(fixed(0), false) == null;
        assert memberOnly.pick(fixed(0), true) == TrailParticle.SONIC_BOOM;
        var large = new WeightedTrailSpec(Map.of(TrailParticle.RED, Integer.MAX_VALUE, TrailParticle.BLUE, Integer.MAX_VALUE));
        assert large.totalWeight(false) == 2L * Integer.MAX_VALUE;
        assert ParticleTrailData.percentage(1, 8).equals("12.5%");
        assert ParticleTrailData.percentage(1, Integer.MAX_VALUE).equals("<0.01%");
        for (var particle : TrailParticle.values()) {
            var encoded = ParticleTypes.CODEC.encodeStart(JsonOps.INSTANCE, particle.options).getOrThrow();
            assert ParticleTypes.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().getType() == particle.options.getType();
        }
        for (var velocity : List.of(new Vec3(1, 0, 0), new Vec3(-2, 1, -3), new Vec3(0, -2, 0), new Vec3(0, 0.01, 0))) {
            for (double fraction : List.of(0.25, 0.75)) {
                Vec3 offset = TrailParticles.behind(Vec3.ZERO, velocity, fraction, true);
                assert offset.dot(velocity) < 0;
                assert offset.length() >= 2;
            }
        }
        assert recipe.getRemainingItems(grid(new ItemStack(Items.ELYTRA), new ItemStack(Items.LAVA_BUCKET))).get(1).is(Items.BUCKET);
        assert recipe.getRemainingItems(grid(new ItemStack(Items.BOW), new ItemStack(Items.HONEY_BOTTLE))).get(1).is(Items.GLASS_BOTTLE);
        assert recipe.getRemainingItems(grid(new ItemStack(Items.BOW), new ItemStack(Items.SULFUR_CUBE_BUCKET))).get(1).is(Items.BUCKET);
        System.out.println("Particle trail checks passed: recipes, legacy data, tooltips, membership, codecs, containers, and positions.");
    }

    private static CraftingInput grid(ItemStack... items) {
        return CraftingInput.of(items.length, 1, List.of(items));
    }

    private static LegacyRandomSource fixed(double value) {
        return new LegacyRandomSource(0) {
            @Override public double nextDouble() { return value; }
        };
    }
}
