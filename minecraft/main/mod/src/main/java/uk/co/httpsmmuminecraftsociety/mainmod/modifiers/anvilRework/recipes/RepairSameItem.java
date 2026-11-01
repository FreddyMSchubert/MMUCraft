package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;

import java.util.ArrayList;
import java.util.List;

public final class RepairSameItem implements AnvilRecipe
{
    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        return left.isDamageableItem()
                && right.isDamageableItem()
                && left.is(right.getItem())
                && !CosmeticsManager.determineCosmeticType(right).isHelmet()
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
        retained.removeIf(enchantment -> resultEnchants.getLevel(enchantment) > 0);
        ItemEnchantments retainedEnchants = retained.toImmutable();
        List<StoredCharmData> resultCharms = new ArrayList<>(CharmorManager.getStoredArmorCharms(result));
        List<StoredCharmData> retainedCharms = new ArrayList<>(CharmStackData.getStoredCharms(right));
        List<StoredCharmData> movedCharms = new ArrayList<>();
        for (StoredCharmData charm : List.copyOf(retainedCharms)) {
            if (resultCharms.size() >= CharmorManager.calcCharmSlotCount(result)) break;
            if (resultCharms.stream().anyMatch(existing -> existing.charmId() == charm.charmId())) continue;
            resultCharms.add(charm);
            retainedCharms.remove(charm);
            movedCharms.add(charm);
        }
        if (!movedCharms.isEmpty()) {
            CharmStackData.setStoredCharms(result, resultCharms);
            CharmsManager.refresh(result);
            changed = true;
        }
        ItemStack rightRemainder = ItemStack.EMPTY;
        if (!retainedEnchants.isEmpty() || !retainedCharms.isEmpty()) {
            rightRemainder = right.copy();
            EnchantmentHelper.setEnchantments(rightRemainder, retainedEnchants);
            if (!movedCharms.isEmpty()) {
                for (var instance : CharmsManager.getCharmInstances(right)) {
                    if (movedCharms.stream().anyMatch(charm -> charm.charmId() == instance.charmId())
                            && instance.charm() instanceof BaseItemChangeCallbackCharm callback) {
                        callback.disableEffectForItem(rightRemainder, instance.level());
                    }
                }
                CharmStackData.setStoredCharms(rightRemainder, retainedCharms);
                CharmorManager.updateArmorTooltip(rightRemainder);
            }
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

        if (!changed || result.isEmpty()) {
            return AnvilLogic.Outcome.EMPTY;
        }

        AnvilUtils.applyRename(result, name);

        CharmorManager.updateArmorTooltip(result);

        return new AnvilLogic.Outcome(
                merge.cost(),
                ItemStack.EMPTY,
                rightRemainder,
                result
        );
    }
}
