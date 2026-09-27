package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;

public final class RepairSameItem implements AnvilRecipe
{
    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        return left.isDamageableItem()
                && right.isDamageableItem()
                && left.is(right.getItem())
                && GliderCharm.isGlider(left) == GliderCharm.isGlider(right);
    }

    @Override
    public AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name)
    {
        ItemStack result = left.copy();
        AnvilUtils.MergeInfo merge = AnvilUtils.mergeEnchantments(result, left, right, false);
        boolean changed = merge.changed();

        ItemEnchantments resultEnchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
        ItemEnchantments.Mutable retained = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(right));
        retained.removeIf(enchantment -> resultEnchants.getLevel(enchantment) > 0
                || resultEnchants.keySet().stream().noneMatch(existing -> !Enchantment.areCompatible(existing, enchantment)));
        ItemEnchantments retainedEnchants = retained.toImmutable();
        ItemStack rightRemainder = ItemStack.EMPTY;
        if (!retainedEnchants.isEmpty()) {
            rightRemainder = right.copy();
            EnchantmentHelper.setEnchantments(rightRemainder, retainedEnchants);
        }

        if (result.isDamageableItem()) {
            int max = result.getMaxDamage();
            int leftRemaining = Math.max(0, max - left.getDamageValue());
            int rightRemaining = Math.max(0, right.getMaxDamage() - right.getDamageValue());
            int available = rightRemainder.isEmpty() ? rightRemaining : Math.max(0, rightRemaining - 1);
            int transferred = Math.min(max - leftRemaining, available);
            int newDamage = max - leftRemaining - transferred;

            if (newDamage != result.getDamageValue()) {
                result.setDamageValue(newDamage);
                changed = true;
            }
            if (!rightRemainder.isEmpty() && transferred > 0) {
                rightRemainder.setDamageValue(right.getDamageValue() + transferred);
            }
        }

        changed |= AnvilUtils.applyRename(result, name);

        if (!changed || result.isEmpty()) {
            return AnvilLogic.Outcome.EMPTY;
        }

        CharmorManager.updateArmorTooltip(result);

        return new AnvilLogic.Outcome(
                merge.cost(),
                ItemStack.EMPTY,
                rightRemainder,
                result
        );
    }
}
