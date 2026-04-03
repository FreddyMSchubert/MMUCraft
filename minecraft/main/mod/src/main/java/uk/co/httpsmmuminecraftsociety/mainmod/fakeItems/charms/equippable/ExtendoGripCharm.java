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
    private static final String EXTENDO_GRIP_BLOCK_INTERACTION_RANGE_ATTRIBUTE_MODIFIER_ID = "extendo_grip_block_interaction_range";

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, EXTENDO_GRIP_BLOCK_INTERACTION_RANGE_ATTRIBUTE_MODIFIER_ID, Attributes.BLOCK_INTERACTION_RANGE, getBlockInteractionRangeAdditionPerLevel(charmLevel), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, EXTENDO_GRIP_BLOCK_INTERACTION_RANGE_ATTRIBUTE_MODIFIER_ID, Attributes.BLOCK_INTERACTION_RANGE);
    }

    private static float getBlockInteractionRangeAdditionPerLevel(int level) {
        if (level < 9)
            return level;
        return 15;
    }
}
