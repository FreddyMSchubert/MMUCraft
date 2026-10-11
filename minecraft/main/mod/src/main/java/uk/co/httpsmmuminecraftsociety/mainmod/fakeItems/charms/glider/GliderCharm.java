package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;

public final class GliderCharm implements Charm, BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 54;

    public static boolean isGlider(ItemStack stack) {
        return stack.is(Items.ELYTRA) && CharmStackData.getStoredCharms(stack).stream()
                .anyMatch(charm -> charm.charmId() == CHARM_ID);
    }

    public static boolean isRealElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA) && !isGlider(stack)
                && !FakeItems.isSpecificFakeItem(stack, "charm-glider");
    }

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        Equippable elytraSettings = Items.ELYTRA.components().get(DataComponents.EQUIPPABLE);
        if (elytraSettings == null) return;

        stack.set(DataComponents.EQUIPPABLE, EquippableCharmItemFeature.withAsset(elytraSettings, "glider"));
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) {
        stack.set(DataComponents.EQUIPPABLE, Items.ELYTRA.components().get(DataComponents.EQUIPPABLE));
    }
}
