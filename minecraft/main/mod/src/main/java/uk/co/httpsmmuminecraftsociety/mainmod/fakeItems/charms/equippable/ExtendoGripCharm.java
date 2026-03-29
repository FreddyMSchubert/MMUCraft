package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class ExtendoGripCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "extendo_grip_block_interaction_range", Attributes.BLOCK_INTERACTION_RANGE, 1.999, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "extendo_grip_block_interaction_range", Attributes.BLOCK_INTERACTION_RANGE);
    }
}
