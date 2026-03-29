package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class KittyPajamasCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "kitty-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER, -1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.LEGS);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "kitty-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER);
    }
}
