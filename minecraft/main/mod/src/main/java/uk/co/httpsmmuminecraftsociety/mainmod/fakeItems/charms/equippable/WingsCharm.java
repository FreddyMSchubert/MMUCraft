package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public final class WingsCharm implements Charm, BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 58;

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            stack.set(DataComponents.GLIDER, Items.ELYTRA.components().get(DataComponents.GLIDER));
        }
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) {
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
            stack.remove(DataComponents.GLIDER);
        }
    }
}
