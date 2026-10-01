package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

public final class GearEnchantmentSanitizer {
    private GearEnchantmentSanitizer() {}

    public static boolean sanitize(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments current = stack.getEnchantments();
        if (current.isEmpty()) return false;

        ItemStack gear = CosmeticsManager.determineCosmeticType(stack).isHelmet()
                ? CosmeticsManager.pumpkinReplicaToHelmet(stack) : stack;
        if (gear.isEmpty()) gear = stack;
        boolean enderite = CharmorManager.isEnderite(gear);
        ItemEnchantments.Mutable corrected = new ItemEnchantments.Mutable(current);
        boolean changed = false;
        for (var entry : current.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int limit = maxAllowedLevel(enchantment, enderite);
            if (DisabledDnTEnchantments.contains(enchantment)
                    || !gear.canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE)
                    || limit <= 0) {
                corrected.removeIf(existing -> existing.equals(enchantment));
                changed = true;
            } else if (entry.getIntValue() > limit) {
                corrected.set(enchantment, limit);
                changed = true;
            }
        }
        if (changed) stack.set(DataComponents.ENCHANTMENTS, corrected.toImmutable());
        return changed;
    }

    static int maxAllowedLevel(Holder<Enchantment> enchantment, boolean enderite) {
        return EnchantmentSettingsManager.getSettingsForEnch(enchantment)
                .map(settings -> enderite ? settings.maxEnderiteLevel : settings.maxNormalGearLevel)
                .orElseGet(() -> enchantment.value().getMaxLevel());
    }

    public static void sanitizePlayer(ServerPlayer player) {
        sanitizeContainer(player.getInventory());
        sanitizeContainer(player.getEnderChestInventory());
    }

    private static void sanitizeContainer(Container container) {
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            changed |= sanitize(container.getItem(slot));
        }
        if (changed) container.setChanged();
    }
}
