package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.commons.lang3.tuple.Triple;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.List;
import java.util.Objects;

public class CharmEnchanting
{
    public static final List<Triple<String, Boolean, ResourceKey<Enchantment>>> fakeItemEnchantModifications = List.of(
        Triple.of("charm-wallet", true, ModEnchantments.SOULBOUND)
    );
    public static final List<Triple<Item, Boolean, ResourceKey<Enchantment>>> vanillaEnchantModifications = List.of(
            Triple.of(Items.RECOVERY_COMPASS, true, ModEnchantments.SOULBOUND)
    );

    public static TriState onAllowEnchanting(Holder<Enchantment> enchantmentHolder, ItemStack itemStack, EnchantingContext enchantingContext)
    {
        for (Triple<Item, Boolean, ResourceKey<Enchantment>> ench : vanillaEnchantModifications) {
            if (!itemStack.getItem().equals(ench.getLeft())) continue;
            if (!enchantmentHolder.is(ench.getRight())) continue;
            return TriState.of(ench.getMiddle());
        }

        CustomModelData cmd = itemStack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) return TriState.DEFAULT;
        FakeItem fitem = FakeItems.ID_MAP.get(cmd.strings().getFirst());

        for (Triple<String, Boolean, ResourceKey<Enchantment>> ench : fakeItemEnchantModifications) {
            if (!Objects.equals(fitem.id(), "charm-wallet")) continue;
            if (!enchantmentHolder.is(ench.getRight())) continue;
            return TriState.of(ench.getMiddle());
        }

        return TriState.DEFAULT;
    }
}
