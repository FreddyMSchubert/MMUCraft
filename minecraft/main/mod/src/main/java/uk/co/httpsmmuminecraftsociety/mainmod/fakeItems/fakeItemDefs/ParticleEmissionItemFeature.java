package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record ParticleEmissionItemFeature(List<Emission> particles) implements ItemFeature {
    public record Emission(String particle, Vec3 from, Vec3 to, int minTicks, int maxTicks,
                           String color, float scale, String arguments) {
        public ParticleOptions options(HolderLookup.Provider registries) {
            if (arguments != null) {
                try {
                    return ParticleArgument.readParticle(new StringReader(particle + arguments), registries);
                } catch (CommandSyntaxException ignored) {
                    return null;
                }
            }
            Identifier id = Identifier.tryParse(particle);
            if (id == null) return null;
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
            if (type == ParticleTypes.DUST) {
                int rgb = color == null ? 0xFF0000 : Integer.parseInt(color.substring(1), 16);
                return new DustParticleOptions(rgb, scale);
            }
            return type instanceof SimpleParticleType simple ? simple : null;
        }
    }

    public static ParticleEmissionItemFeature of(JsonObject json) {
        List<Emission> particles = new ArrayList<>();
        for (var element : json.getAsJsonArray("particles")) {
            JsonObject entry = element.getAsJsonObject();
            particles.add(new Emission(
                    entry.get("particle").getAsString(),
                    point(entry.getAsJsonArray("from")),
                    point(entry.getAsJsonArray("to")),
                    entry.get("minTicks").getAsInt(),
                    entry.get("maxTicks").getAsInt(),
                    entry.has("color") ? entry.get("color").getAsString() : null,
                    entry.has("scale") ? entry.get("scale").getAsFloat() : 1.0f,
                    entry.has("arguments") ? entry.get("arguments").getAsString() : null
            ));
        }
        ParticleEmissionItemFeature feature = new ParticleEmissionItemFeature(List.copyOf(particles));
        feature.validate();
        return feature;
    }

    private static Vec3 point(JsonArray array) {
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    @Override public void apply(ItemStack stack) {}

    @Override public void validate() {
        if (particles.isEmpty()) throw new IllegalStateException("Particle emission needs at least one entry.");
        for (Emission emission : particles) {
            if (emission.minTicks < 1 || emission.maxTicks < emission.minTicks || emission.maxTicks > 12000)
                throw new IllegalStateException("Invalid particle emission tick range: " + emission.particle);
            if (emission.from.x > emission.to.x || emission.from.y > emission.to.y || emission.from.z > emission.to.z)
                throw new IllegalStateException("Particle emission from must be at or below to on every axis: " + emission.particle);
            for (double coordinate : new double[] {emission.from.x, emission.from.y, emission.from.z,
                    emission.to.x, emission.to.y, emission.to.z}) {
                if (!Double.isFinite(coordinate) || coordinate < -64 || coordinate > 64)
                    throw new IllegalStateException("Particle emission coordinate must be between -64 and 64: " + emission.particle);
            }
            if (emission.color != null && !emission.color.matches("#[0-9A-Fa-f]{6}"))
                throw new IllegalStateException("Particle colour must be #RRGGBB: " + emission.particle);
            if (!Float.isFinite(emission.scale) || emission.scale <= 0 || emission.scale > 4)
                throw new IllegalStateException("Particle scale must be greater than 0 and at most 4: " + emission.particle);
            if (emission.arguments != null && (emission.color != null || emission.scale != 1))
                throw new IllegalStateException("Particle arguments cannot be combined with colour or scale: " + emission.particle);
        }
    }
}
