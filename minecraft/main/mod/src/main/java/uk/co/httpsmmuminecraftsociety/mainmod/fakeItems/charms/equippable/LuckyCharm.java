package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class LuckyCharm implements Charm, BaseItemChangeCallbackCharm
{
    private static final String LUCKY_CHARM_LUCK_ATTRIBUTE_MODIFIER_ID = "lucky_charm_luck";

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, LUCKY_CHARM_LUCK_ATTRIBUTE_MODIFIER_ID, Attributes.LUCK, Math.min(charmLevel, 5), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, LUCKY_CHARM_LUCK_ATTRIBUTE_MODIFIER_ID, Attributes.LUCK);
    }
}
