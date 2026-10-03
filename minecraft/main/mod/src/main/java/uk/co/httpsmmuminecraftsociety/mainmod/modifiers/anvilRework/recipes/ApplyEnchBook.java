package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.EnchantmentLock;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;

public final class ApplyEnchBook implements AnvilRecipe
{
    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        return !EnchantmentHelper.getEnchantmentsForCrafting(right).isEmpty();
    }

    @Override
    public AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name)
    {
        if (EnchantmentLock.isLocked(left)) {
            return AnvilLogic.Outcome.EMPTY;
        }

        boolean resultIsBook = left.is(Items.BOOK) || left.is(Items.ENCHANTED_BOOK);
        ItemStack result = resultIsBook ? new ItemStack(Items.ENCHANTED_BOOK) : left.copy();

        AnvilUtils.MergeInfo merge = AnvilUtils.mergeEnchantments(result, left, right, resultIsBook);
        if (result.isEmpty() || Objects.equals(
                EnchantmentHelper.getEnchantmentsForCrafting(left),
                EnchantmentHelper.getEnchantmentsForCrafting(result))) {
            return AnvilLogic.Outcome.EMPTY;
        }

        AnvilUtils.applyRename(result, name);

        CharmorManager.updateArmorTooltip(result);

        ItemStack rightRemainder = ItemStack.EMPTY;
        if (right.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable retained = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(right));
            ItemEnchantments resultEnchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
            retained.removeIf(enchantment -> resultEnchants.getLevel(enchantment) > 0);
            ItemEnchantments retainedEnchants = retained.toImmutable();
            if (!retainedEnchants.isEmpty()) {
                rightRemainder = right.copy();
                EnchantmentHelper.setEnchantments(rightRemainder, retainedEnchants);
            }
        } else if (!right.is(Items.BOOK)) {
            rightRemainder = EnchantmentLock.removeAllEnchantmentsAndUnlock(right.copy());
        }

        return new AnvilLogic.Outcome(
                merge.cost(),
                ItemStack.EMPTY,
                rightRemainder,
                result
        );
    }
}
