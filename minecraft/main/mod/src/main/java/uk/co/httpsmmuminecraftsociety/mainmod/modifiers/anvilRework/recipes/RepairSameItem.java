package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;

public final class RepairSameItem implements AnvilRecipe
{
    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        return left.isDamageableItem()
                && right.isDamageableItem()
                && left.is(right.getItem());
    }

    @Override
    public AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name)
    {
        ItemStack result = left.copy();
        boolean changed = false;

        if (result.isDamageableItem()) {
            int max = result.getMaxDamage();
            int leftRemaining = Math.max(0, max - left.getDamageValue());
            int rightRemaining = Math.max(0, max - right.getDamageValue());
            int mergedRemaining = Math.min(max, leftRemaining + rightRemaining);
            int newDamage = max - mergedRemaining;

            if (newDamage != result.getDamageValue()) {
                result.setDamageValue(newDamage);
                changed = true;
            }
        }

        AnvilUtils.MergeInfo merge = AnvilUtils.mergeEnchantments(result, left, right, false);
        changed |= merge.changed();

        changed |= AnvilUtils.applyRename(result, name);

        if (!changed || result.isEmpty()) {
            return AnvilLogic.Outcome.EMPTY;
        }

        CharmorManager.updateArmorTooltip(result);

        return new AnvilLogic.Outcome(
                merge.cost(),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                result
        );
    }
}
