package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ParticleTrailData {
    // Keep the saved key so that existing bows retain their colours.
    private static final String DATA_KEY = "arrow_trail";
    private static final String LORE_KEY = "mainmod.particle_trail.";

    private ParticleTrailData() {}

    public static boolean supports(ItemStack stack) {
        return stack != null && (stack.is(Items.BOW) || stack.is(Items.ELYTRA));
    }

    public static WeightedTrailSpec getTrailSpec(ItemStack stack) {
        if (!supports(stack)) return WeightedTrailSpec.EMPTY;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = data.getList(DATA_KEY).orElseGet(ListTag::new);
        Map<TrailParticle, Integer> weights = new EnumMap<>(TrailParticle.class);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index).orElseGet(CompoundTag::new);
            String id = entry.getString("particle").orElseGet(() -> entry.getString("dye").orElse(""));
            WeightedTrailSpec.addWeight(weights, TrailParticle.fromId(id), entry.getInt("weight").orElse(0));
        }
        return new WeightedTrailSpec(weights);
    }

    public static void setTrailSpec(ItemStack stack, WeightedTrailSpec spec) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = new ListTag();
        spec.weights().forEach((particle, weight) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("particle", particle.id());
            entry.putInt("weight", weight);
            list.add(entry);
        });
        if (list.isEmpty()) data.remove(DATA_KEY);
        else data.put(DATA_KEY, list);
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateTooltip(stack);
    }

    public static void updateTooltip(ItemStack stack) {
        if (!supports(stack)) return;
        WeightedTrailSpec spec = getTrailSpec(stack);
        ItemLore previous = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(previous.lines());
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && text.getKey().startsWith(LORE_KEY));
        if (spec.totalWeight(true) > 0) {
            lines.add(lore("header", "Particle trail:", ChatFormatting.GOLD));
            spec.weights().forEach((particle, weight) -> lines.add(lore(particle.id(),
                    particle.label + " (" + percentage(weight, spec.totalWeight(true)) + ")"
                            + (particle.isBasicDust() ? "" : " [Member]"), ChatFormatting.GRAY)));
            if (spec.totalWeight(false) != spec.totalWeight(true)) {
                lines.add(lore("member", "Non-members: dust only; chances adjust.", ChatFormatting.DARK_GRAY));
            }
        }
        if (lines.equals(previous.lines())) return;
        if (lines.isEmpty()) stack.remove(DataComponents.LORE);
        else stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static Component lore(String key, String text, ChatFormatting color) {
        return Component.translatableWithFallback(LORE_KEY + key, "%s", text)
                .withStyle(style -> style.withColor(color).withItalic(false));
    }

    public static String percentage(int weight, long total) {
        double percent = 100.0 * weight / total;
        if (percent < 0.01) return "<0.01%";
        return String.format(Locale.ROOT, "%.2f", percent).replaceAll("\\.?0+$", "") + "%";
    }
}
