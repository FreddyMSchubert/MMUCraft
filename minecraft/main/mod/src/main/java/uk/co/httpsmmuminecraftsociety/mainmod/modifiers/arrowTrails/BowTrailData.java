package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.arrowTrails;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public final class BowTrailData {
    public static final String BOW_TRAIL_DATA_KEY = "arrow_trail";

    public static WeightedTrailSpec getTrailSpec(ItemStack stack) {
        if (stack.isEmpty()) {
            return WeightedTrailSpec.EMPTY;
        }

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (nbt.getList(BOW_TRAIL_DATA_KEY).isEmpty()) return WeightedTrailSpec.EMPTY;
        ListTag list = nbt.getList(BOW_TRAIL_DATA_KEY).get();
        if (list.isEmpty()) return WeightedTrailSpec.EMPTY;

        List<WeightedTrailSpec.Entry> entries = new ArrayList<>();
        int totalWeight = 0;

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i).get();

            if (entryTag.getString("dye").isEmpty()) {
                continue;
            }

            String dyeName = entryTag.getString("dye").get();
            int weight = entryTag.getInt("weight").get();

            if (weight <= 0) {
                continue;
            }

            var dye = WeightedTrailSpec.parseDye(dyeName);
            if (dye == null) {
                continue;
            }

            entries.add(new WeightedTrailSpec.Entry(dye, weight));
            totalWeight += weight;
        }

        return entries.isEmpty() ? WeightedTrailSpec.EMPTY : new WeightedTrailSpec(entries, totalWeight);
    }

    public static ItemStack findBowStack(LivingEntity shooter) {
        ItemStack using = shooter.getUseItem();
        if (!using.isEmpty() && using.getItem() instanceof BowItem) {
            return using;
        }

        ItemStack main = shooter.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof BowItem) {
            return main;
        }

        ItemStack off = shooter.getOffhandItem();
        if (!off.isEmpty() && off.getItem() instanceof BowItem) {
            return off;
        }

        return ItemStack.EMPTY;
    }
}
