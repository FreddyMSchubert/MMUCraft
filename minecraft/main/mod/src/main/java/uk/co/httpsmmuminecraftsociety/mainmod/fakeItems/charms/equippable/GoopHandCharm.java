package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class GoopHandCharm implements Charm, BaseItemChangeCallbackCharm
{
    private static final String GOOP_HAND_ATTACK_KNOCKBACK_ATTRIBUTE_MODIFIER_ID = "goop_hand_attack_knockback";

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, GOOP_HAND_ATTACK_KNOCKBACK_ATTRIBUTE_MODIFIER_ID, Attributes.ATTACK_KNOCKBACK, getBlockInteractionRangeAdditionPerLevel(charmLevel), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, GOOP_HAND_ATTACK_KNOCKBACK_ATTRIBUTE_MODIFIER_ID, Attributes.ATTACK_KNOCKBACK);
    }

    private static float getBlockInteractionRangeAdditionPerLevel(int level) {
        return level * 0.4f;
    }
}
