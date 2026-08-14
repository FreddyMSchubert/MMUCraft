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
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        boolean changed = normalizeEnchantmentLevels(result, mutable);

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

            int cappedLevel = capAnvilLevel(ench, result, level);
            if (cappedLevel > 0) {
                mutable.set(ench, cappedLevel);
            }
        }

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

            int newLevel = capAnvilLevel(ench, result, leftLevel + rightLevel);
            if (newLevel > leftLevel) {
                mutable.set(ench, newLevel);
                changed = true;
            } else if (leftLevel > 0 && newLevel <= 0) {
                mutable.removeIf(existing -> existing.equals(ench));
                changed = true;
            }
        }

        ItemEnchantments merged = mutable.toImmutable();
        EnchantmentHelper.setEnchantments(result, merged);

        if (resultIsBook && (!leftEnchants.isEmpty() || !rightEnchants.isEmpty())) {
            changed = !merged.isEmpty();
        }

        int cost = totalChangedEnchantCost(
                leftEnchants,
                rightEnchants,
                merged,
                right.has(DataComponents.STORED_ENCHANTMENTS)
        );
        return new MergeInfo(cost, changed);
    }

    private static int capAnvilLevel(Holder<Enchantment> enchantment, ItemStack stack, int level) {
        return Math.min(level, EnchantmentSettingsManager.getMaxAnvilLevel(enchantment));
    }

    private static boolean normalizeEnchantmentLevels(ItemStack stack, ItemEnchantments.Mutable mutable) {
        boolean changed = false;
        List<Holder<Enchantment>> enchantments = new ArrayList<>(mutable.keySet());

        for (Holder<Enchantment> enchantment : enchantments) {
            int currentLevel = mutable.getLevel(enchantment);
            int cappedLevel = capAnvilLevel(enchantment, stack, currentLevel);

            if (cappedLevel <= 0) {
                mutable.removeIf(existing -> existing.equals(enchantment));
                changed = true;
                continue;
            }

            if (cappedLevel < currentLevel) {
                mutable.set(enchantment, cappedLevel);
                changed = true;
            }
        }

        return changed;
    }

    private static int totalChangedEnchantCost(
            ItemEnchantments leftEnchants,
            ItemEnchantments rightEnchants,
            ItemEnchantments resultEnchants,
            boolean rightIsEnchantedBook
    ) {
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
                int anvilCost = ench.value().getAnvilCost();
                if (rightIsEnchantedBook) {
                    anvilCost = Math.max(1, anvilCost / 2);
                }
                cost += anvilCost * chargedLevels;
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
