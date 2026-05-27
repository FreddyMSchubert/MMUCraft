package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.EnchantmentLock;

import java.util.HashSet;
import java.util.Set;

public class AnvilUtils
{
    public record MergeInfo(int cost, boolean changed) {}

    public static MergeInfo mergeEnchantments(ItemStack result, ItemStack left, ItemStack right, boolean resultIsBook) {
        if (!resultIsBook && EnchantmentLock.isLocked(left)) {
            return new MergeInfo(0, false);
        }

        ItemEnchantments base = resultIsBook
                ? new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(ItemStack.EMPTY)).toImmutable()
                : EnchantmentHelper.getEnchantmentsForCrafting(result);

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(base);

        ItemEnchantments leftEnchants = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnchants = EnchantmentHelper.getEnchantmentsForCrafting(right);

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : leftEnchants.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            int level = entry.getIntValue();

            if (!resultIsBook && !ench.value().isSupportedItem(result)) {
                continue;
            }
            if (!resultIsBook && conflictsWithExisting(mutable, ench)) {
                continue;
            }

            mutable.set(ench, Math.min(level, ench.value().getMaxLevel()));
        }

        boolean changed = false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchants.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            Enchantment value = ench.value();

            int leftLevel = mutable.getLevel(ench);
            int rightLevel = entry.getIntValue();

            if (!resultIsBook) {
                if (!value.isSupportedItem(result)) {
                    continue;
                }
                if (leftLevel == 0 && conflictsWithExisting(mutable, ench)) {
                    continue;
                }
            }

            int newLevel = Math.min(value.getMaxLevel(), leftLevel + rightLevel);
            if (newLevel > leftLevel) {
                mutable.set(ench, newLevel);
                changed = true;
            }
        }

        ItemEnchantments merged = mutable.toImmutable();
        EnchantmentHelper.setEnchantments(result, merged);

        if (resultIsBook && (!leftEnchants.isEmpty() || !rightEnchants.isEmpty())) {
            changed = !merged.isEmpty();
        }

        int cost = totalChangedEnchantCost(leftEnchants, rightEnchants, merged);
        return new MergeInfo(cost, changed);
    }

    private static int totalChangedEnchantCost(ItemEnchantments leftEnchants, ItemEnchantments rightEnchants, ItemEnchantments resultEnchants) {
        int cost = 0;
        Set<Holder<Enchantment>> all = new HashSet<>();

        all.addAll(leftEnchants.keySet());
        all.addAll(rightEnchants.keySet());

        for (Holder<Enchantment> ench : all) {
            int leftLevel = leftEnchants.getLevel(ench);
            int rightLevel = rightEnchants.getLevel(ench);
            int resultLevel = resultEnchants.getLevel(ench);

            if (resultLevel <= 0) {
                continue;
            }

            int chargedLevels;
            if (leftLevel > 0 && rightLevel > 0) {
                chargedLevels = Math.max(0, resultLevel - Math.max(leftLevel, rightLevel));
            } else {
                chargedLevels = resultLevel;
            }

            if (chargedLevels > 0) {
                cost += ench.value().getAnvilCost() * chargedLevels;
            }
        }

        return cost;
    }

    private static boolean conflictsWithExisting(ItemEnchantments.Mutable mutable, Holder<Enchantment> candidate) {
        for (Holder<Enchantment> existing : mutable.keySet()) {
            if (existing.equals(candidate)) {
                continue;
            }
            if (!Enchantment.areCompatible(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean applyRename(ItemStack result, @Nullable String name) {
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.isEmpty()) {
            boolean hadCustomName = result.has(DataComponents.CUSTOM_NAME);
            result.remove(DataComponents.CUSTOM_NAME);
            return hadCustomName;
        }

        Component newName = Component.literal(trimmed);
        Component oldName = result.get(DataComponents.CUSTOM_NAME);
        result.set(DataComponents.CUSTOM_NAME, newName);
        return !newName.equals(oldName);
    }
}
