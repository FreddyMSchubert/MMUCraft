package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;

import java.util.List;

public final class RepairMaterial implements AnvilRecipe
{
    private static final int NETHERITE_REPAIRS_NEEDED_WITH_DIAMOND_INCREASE_DIVISOR = 3;
    private static final int DEFAULT_REPAIR_MATERIAL_COUNT_NEEDED = 4;

    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        if (!isVanillaAnvilRepairable(left)) {
            return false;
        }

        Repairable repairable = left.get(DataComponents.REPAIRABLE);
        if (repairable != null && repairable.isValidRepairItem(right)) {
            return true;
        }

        return isNetheriteGear(left) && right.is(Items.DIAMOND);
    }

    @Override
    public AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name)
    {
        System.out.println("[ANVIL RECIPE] RepairMaterial apply hit");
        if (!left.isDamageableItem() || !left.isDamaged()) {
            return AnvilLogic.Outcome.EMPTY;
        }

        int repairPerUnit = getRepairAmountPerUnit(left, right);
        if (repairPerUnit <= 0) {
            return AnvilLogic.Outcome.EMPTY;
        }

        int damage = left.getDamageValue();
        int neededUnits = (damage + repairPerUnit - 1) / repairPerUnit;
        int usedUnits = Math.min(neededUnits, right.getCount());

        if (usedUnits <= 0) {
            return AnvilLogic.Outcome.EMPTY;
        }

        ItemStack result = left.copy();
        int newDamage = Math.max(0, damage - usedUnits * repairPerUnit);
        result.setDamageValue(newDamage);

        boolean changed = newDamage != left.getDamageValue();
        changed |= AnvilUtils.applyRename(result, name);

        if (!changed) {
            return AnvilLogic.Outcome.EMPTY;
        }

        ItemStack rightRemainder = right.copy();
        rightRemainder.shrink(usedUnits);
        if (rightRemainder.isEmpty()) {
            rightRemainder = ItemStack.EMPTY;
        }

        return new AnvilLogic.Outcome(
                0,
                ItemStack.EMPTY,
                rightRemainder,
                result
        );
    }

    private static boolean isNetheriteGear(ItemStack stack) {
        return stack.is(Items.NETHERITE_HELMET)
                || stack.is(Items.NETHERITE_CHESTPLATE)
                || stack.is(Items.NETHERITE_LEGGINGS)
                || stack.is(Items.NETHERITE_BOOTS)
                || stack.is(Items.NETHERITE_SWORD)
                || stack.is(Items.NETHERITE_PICKAXE)
                || stack.is(Items.NETHERITE_AXE)
                || stack.is(Items.NETHERITE_SHOVEL)
                || stack.is(Items.NETHERITE_HOE);
    }

    private static boolean isVanillaAnvilRepairable(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        return stack.get(DataComponents.REPAIRABLE) != null;
    }

    private static int getRepairAmountPerUnit(ItemStack target, ItemStack ingredient) {
        if (isNetheriteGear(target) && ingredient.is(Items.DIAMOND)) {
            int perIngot = getRepairAmountPerUnit(target, Items.NETHERITE_INGOT.getDefaultInstance());
            return Math.max(1, perIngot / NETHERITE_REPAIRS_NEEDED_WITH_DIAMOND_INCREASE_DIVISOR);
        }
        return Math.max(1, target.getMaxDamage() / Math.max(1, countRepairUnitsForItem(target.getItem())));
    }

    private static final List<Tuple<Integer, Tuple<List<TagKey<Item>>, List<Item>>>> repairCounts = List.of(
        new Tuple<>(1, new Tuple<>(List.of(ItemTags.SHOVELS, ItemTags.SPEARS), List.of())),
        new Tuple<>(2, new Tuple<>(List.of(ItemTags.HOES, ItemTags.SWORDS), List.of(Items.ELYTRA, Items.MACE))),
        new Tuple<>(3, new Tuple<>(List.of(ItemTags.PICKAXES, ItemTags.AXES), List.of())),
        new Tuple<>(4, new Tuple<>(List.of(ItemTags.FOOT_ARMOR), List.of())),
        new Tuple<>(5, new Tuple<>(List.of(ItemTags.HEAD_ARMOR), List.of())),
        new Tuple<>(6, new Tuple<>(List.of(), List.of(Items.SHIELD, Items.WOLF_ARMOR))),
        new Tuple<>(7, new Tuple<>(List.of(ItemTags.LEG_ARMOR), List.of())),
        new Tuple<>(8, new Tuple<>(List.of(ItemTags.CHEST_ARMOR), List.of()))
    );
    private static int countRepairUnitsForItem(Item item) {
        for (Tuple<Integer, Tuple<List<TagKey<Item>>, List<Item>>> counts : repairCounts) {
            for (TagKey<Item> tag : counts.getB().getA())
                if (item.getDefaultInstance().is(tag)) return counts.getA();
            for (Item matchItem : counts.getB().getB())
                if (item.equals(matchItem)) return counts.getA();
        }
        return DEFAULT_REPAIR_MATERIAL_COUNT_NEEDED;
    }
}
