package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class GliderCharm implements Charm {
    public static final int CHARM_ID = 54;

    public static boolean isGlider(ItemStack stack) {
        return stack.is(Items.ELYTRA) && CharmStackData.getStoredCharms(stack).stream()
                .anyMatch(charm -> charm.charmId() == CHARM_ID);
    }

    public static TriState allowEnchanting(Holder<Enchantment> enchantment, ItemStack stack, EnchantingContext context) {
        if (!isGlider(stack)) return TriState.DEFAULT;
        return TriState.of(enchantment.is(Enchantments.MENDING) || enchantment.is(ModEnchantments.SOULBOUND));
    }
}
